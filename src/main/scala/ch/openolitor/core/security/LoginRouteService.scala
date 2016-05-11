/*                                                                           *\
*    ____                   ____  ___ __                                      *
*   / __ \____  ___  ____  / __ \/ (_) /_____  _____                          *
*  / / / / __ \/ _ \/ __ \/ / / / / / __/ __ \/ ___/   OpenOlitor             *
* / /_/ / /_/ /  __/ / / / /_/ / / / /_/ /_/ / /       contributed by tegonal *
* \____/ .___/\___/_/ /_/\____/_/_/\__/\____/_/        http://openolitor.ch   *
*     /_/                                                                     *
*                                                                             *
* This program is free software: you can redistribute it and/or modify it     *
* under the terms of the GNU General Public License as published by           *
* the Free Software Foundation, either version 3 of the License,              *
* or (at your option) any later version.                                      *
*                                                                             *
* This program is distributed in the hope that it will be useful, but         *
* WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY  *
* or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for *
* more details.                                                               *
*                                                                             *
* You should have received a copy of the GNU General Public License along     *
* with this program. If not, see http://www.gnu.org/licenses/                 *
*                                                                             *
\*                                                                           */
package ch.openolitor.core.security

import spray.routing._
import spray.http._
import spray.http.MediaTypes._
import spray.httpx.marshalling.ToResponseMarshallable._
import spray.httpx.SprayJsonSupport._
import spray.routing.Directive._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.caching._
import ch.openolitor.core.db.ConnectionPoolContextAware
import ch.openolitor.core._
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.stammdaten.StammdatenReadRepositoryComponent
import ch.openolitor.stammdaten.DefaultStammdatenReadRepositoryComponent
import akka.actor.ActorRef
import ch.openolitor.core.filestore.FileStore
import akka.actor.ActorRefFactory
import ch.openolitor.stammdaten.models.Person
import org.mindrot.jbcrypt.BCrypt
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import ch.openolitor.core.db.AsyncConnectionPoolContextAware
import ch.openolitor.core.models.PersonId
import java.util.UUID
import ch.openolitor.stammdaten.models.PersonDetail
import ch.openolitor.core.Macros._
import scala.util.Random
import scalaz._
import Scalaz._
import ch.openolitor.util.ConfigUtil._

trait LoginRouteService extends HttpService with ActorReferences
    with AsyncConnectionPoolContextAware
    with SprayDeserializers
    with DefaultRouteService with LazyLogging with LoginJsonProtocol {
  self: StammdatenReadRepositoryComponent =>

  //TODO: get real userid from login
  override val personId: PersonId = Boot.systemPersonId

  type EitherFuture[A] = EitherT[Future, LoginFailed, A]

  lazy val loginRoutes = loginRoute

  val loginTokenCache = LruCache[PersonId](
    maxCapacity = 10000,
    timeToLive = 1 day,
    timeToIdle = 4 hours
  )
  val secondFactorTokenCache = LruCache[SecondFactor](
    maxCapacity = 1000,
    timeToLive = 10 minutes,
    timeToIdle = 10 minutes
  )

  lazy val config = sysConfig.mandantConfiguration.config
  lazy val requireSecondFactorAuthentication = config.getBooleanOption(s"security.second-factor-auth.require").getOrElse(true)
  lazy val sendSecondFactorEmail = config.getBooleanOption(s"security.second-factor-auth.send-email").getOrElse(true)

  val errorUsernameOrPasswordMismatch = LoginFailed("Benutzername oder Passwort stimmen nicht überein")
  val errorTokenOrCodeMismatch = LoginFailed("Code stimmt nicht überein")
  val errorPersonNotFound = LoginFailed("Person konnte nicht gefunden werden")

  def loginRoute = pathPrefix("auth") {
    path("login") {
      post {
        requestInstance { request =>
          entity(as[LoginForm]) { form =>
            onSuccess(validateLogin(form).run) {
              case -\/(error) =>
                complete(StatusCodes.BadRequest, error.msg)
              case \/-(result) =>
                complete(result)
            }
          }
        }
      }
    } ~
      path("secondFactor") {
        post {
          requestInstance { request =>
            entity(as[SecondFactorLoginForm]) { form =>
              onSuccess(validateSecondFactorLogin(form).run) {
                case -\/(error) =>
                  complete(StatusCodes.BadRequest, error.msg)
                case \/-(result) =>
                  complete(result)
              }
            }
          }
        }
      }
  }

  def validateLogin(form: LoginForm): EitherFuture[LoginResult] = {
    for {
      person <- personByEmail(form)
      pwdValid <- validatePassword(form, person)
      result <- handleLoggedIn(person)
    } yield result
  }

  def transform[A](o: Option[Future[A]]): Future[Option[A]] = {
    o.map(f => f.map(Option(_))).getOrElse(Future.successful(None))
  }

  def validateSecondFactorLogin(form: SecondFactorLoginForm): EitherFuture[LoginResult] = {
    for {
      secondFactor <- readTokenFromCache(form)
      person <- personById(personId)
      result <- doLogin(person)
    } yield result
  }

  def readTokenFromCache(form: SecondFactorLoginForm): EitherFuture[SecondFactor] = {
    EitherT {
      transform(secondFactorTokenCache.get(form.token)) map {
        case Some(factor @ SecondFactor(form.token, form.code, _)) => factor.right
        case _ => errorTokenOrCodeMismatch.left
      }
    }
  }

  def personByEmail(form: LoginForm): EitherFuture[Person] = {
    EitherT {
      stammdatenReadRepository.getPersonByEmail(form.email) map (_ map (_.right) getOrElse (errorUsernameOrPasswordMismatch.left))
    }
  }

  def personById(personId: PersonId): EitherFuture[PersonDetail] = {
    EitherT {
      stammdatenReadRepository.getPerson(personId) map (_ map (_.right) getOrElse (errorPersonNotFound.left))
    }
  }

  def handleLoggedIn(person: Person): EitherFuture[LoginResult] = {
    val detail = copyTo[Person, PersonDetail](person)
    requireSecondFactorAuthentifcation(person) flatMap {
      case false => doLogin(detail)
      case true => sendSecondFactorAuthentication(detail)
    }
  }

  def doLogin(person: PersonDetail): EitherFuture[LoginResult] = {
    //generate token
    val token = generateToken
    EitherT {
      loginTokenCache(token)(person.id) map { _ =>
        LoginResult(LoginOk, token, person).right
      }
    }
  }

  def sendSecondFactorAuthentication(person: PersonDetail): EitherFuture[LoginResult] = {
    for {
      secondFactor <- generateSecondFactor(person)
      emailSent <- sendEmail(secondFactor, person)
    } yield {
      LoginResult(LoginSecondFactorRequired, secondFactor.token, person)
    }
  }

  def generateSecondFactor(person: PersonDetail): EitherFuture[SecondFactor] = {
    EitherT {
      val token = generateToken
      val code = generateCode
      secondFactorTokenCache(token)(SecondFactor(token, code, person.id)) map (_.right)
    }
  }

  def sendEmail(secondFactor: SecondFactor, person: PersonDetail): EitherFuture[Boolean] = EitherT {
    Future {
      logger.debug(s"=====================================================================")
      logger.debug(s"| Send Email to:${person.email}")
      logger.debug(s"---------------------------------------------------------------------")
      logger.debug(s"| Token :${secondFactor.token}")
      logger.debug(s"| Code :${secondFactor.code}")
      logger.debug(s"=====================================================================")

      //TODO: bind to email service

      true.right
    }
  }

  def requireSecondFactorAuthentifcation(person: Person): EitherFuture[Boolean] = EitherT {
    requireSecondFactorAuthentication match {
      case false => Future.successful(false.right)
      case true if (person.rolle.isEmpty) => Future.successful(true.right)
      case true => stammdatenReadRepository.getProjekt map {
        case None => true.right
        case Some(projekt) => projekt.twoFactorAuthentication.get(person.rolle.get).map(_.right).getOrElse(true.right)
      }
    }
  }

  def validatePassword(form: LoginForm, person: Person): EitherFuture[Boolean] = EitherT {
    Future {
      person.passwort map { pwd =>
        BCrypt.checkpw(form.passwort, new String(pwd)) match {
          case true => true.right
          case false => errorUsernameOrPasswordMismatch.left
        }
      } getOrElse errorUsernameOrPasswordMismatch.left
    }
  }

  def generateToken = UUID.randomUUID.toString
  def generateCode = (Random.alphanumeric take 6).mkString.toLowerCase
}

class DefaultLoginRouteService(
  override val entityStore: ActorRef,
  override val sysConfig: SystemConfig,
  override val fileStore: FileStore,
  override val actorRefFactory: ActorRefFactory
)
    extends LoginRouteService
    with DefaultStammdatenReadRepositoryComponent