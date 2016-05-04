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
package ch.openolitor.buchhaltung

import ch.openolitor.core.models._
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.async.FutureImplicits._
import scala.concurrent.ExecutionContext
import ch.openolitor.core.db._
import ch.openolitor.core.db.OOAsyncDB._
import ch.openolitor.core.repositories._
import ch.openolitor.core.repositories.BaseRepository._
import ch.openolitor.core.repositories.BaseWriteRepository
import scala.concurrent._
import akka.event.Logging
import ch.openolitor.stammdaten.models._
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.core.EventStream
import ch.openolitor.core.Boot
import akka.actor.ActorSystem
import ch.openolitor.buchhaltung.models._
import ch.openolitor.core.Macros._
import ch.openolitor.stammdaten.StammdatenDBMappings

trait BuchhaltungReadRepository {
  def getRechnungen(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Rechnung]]
  def getRechnungDetail(id: RechnungId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[RechnungDetail]]
}

trait BuchhaltungWriteRepository extends BaseWriteRepository {
  def cleanupDatabase(implicit cpContext: ConnectionPoolContext)
}

class BuchhaltungReadRepositoryImpl extends BuchhaltungReadRepository with LazyLogging with BuchhaltungDBMappings with StammdatenDBMappings {

  lazy val rechnung = rechnungMapping.syntax("rechnung")
  lazy val kunde = kundeMapping.syntax("kunde")
  lazy val depotlieferungAbo = depotlieferungAboMapping.syntax("depotlieferungAbo")
  lazy val heimlieferungAbo = heimlieferungAboMapping.syntax("heimlieferungAbo")
  lazy val postlieferungAbo = postlieferungAboMapping.syntax("postlieferungAbo")

  def getRechnungen(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Rechnung]] = {
    withSQL {
      select
        .from(rechnungMapping as rechnung)
        .orderBy(rechnung.rechnungsDatum)
    }.map(rechnungMapping(rechnung)).list.future
  }

  def getRechnungDetail(id: RechnungId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[RechnungDetail]] = {
    withSQL {
      select
        .from(rechnungMapping as rechnung)
        .leftJoin(kundeMapping as kunde).on(rechnung.kundeId, kunde.id)
        .leftJoin(depotlieferungAboMapping as depotlieferungAbo).on(rechnung.aboId, depotlieferungAbo.id)
        .leftJoin(heimlieferungAboMapping as heimlieferungAbo).on(rechnung.aboId, heimlieferungAbo.id)
        .leftJoin(postlieferungAboMapping as postlieferungAbo).on(rechnung.aboId, postlieferungAbo.id)
        .where.eq(rechnung.id, parameter(id))
        .orderBy(rechnung.rechnungsDatum)
    }.one(rechnungMapping(rechnung))
      .toManies(
        rs => kundeMapping.opt(kunde)(rs),
        rs => postlieferungAboMapping.opt(postlieferungAbo)(rs),
        rs => heimlieferungAboMapping.opt(heimlieferungAbo)(rs),
        rs => depotlieferungAboMapping.opt(depotlieferungAbo)(rs)
      )
      .map({ (rechnung, kunden, pl, hl, dl) =>
        val kunde = kunden.head
        val abo = (pl ++ hl ++ dl).head
        copyTo[Rechnung, RechnungDetail](rechnung, "kunde" -> kunde, "abo" -> abo)
      }).single.future
  }
}

class BuchhaltungWriteRepositoryImpl(val system: ActorSystem) extends BuchhaltungWriteRepository with LazyLogging with EventStream with BuchhaltungDBMappings {
  override def cleanupDatabase(implicit cpContext: ConnectionPoolContext) = {
    DB localTx { implicit session =>
      sql"truncate table ${rechnungMapping.table}".execute.apply()
    }
  }
}
