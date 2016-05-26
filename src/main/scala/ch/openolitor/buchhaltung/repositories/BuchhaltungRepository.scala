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
package ch.openolitor.buchhaltung.repositories

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
import ch.openolitor.core.AkkaEventStream

/**
 * Asynchronous Repository
 */
trait BuchhaltungReadRepository {
  def getRechnungen(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Rechnung]]
  def getKundenRechnungen(kundeId: KundeId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Rechnung]]
  def getRechnungDetail(id: RechnungId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[RechnungDetail]]
  def getRechnungByReferenznummer(referenzNummer: String)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[Rechnung]]

  def getZahlungsImports(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[ZahlungsImport]]
  def getZahlungsImportDetail(id: ZahlungsImportId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[ZahlungsImportDetail]]
}

/**
 * Synchronous Repository
 */
trait BuchhaltungWriteRepository extends BaseWriteRepository with EventStream {
  def cleanupDatabase(implicit cpContext: ConnectionPoolContext)

  def getRechnungen(implicit session: DBSession, cpContext: ConnectionPoolContext): List[Rechnung]
  def getKundenRechnungen(kundeId: KundeId)(implicit session: DBSession, cpContext: ConnectionPoolContext): List[Rechnung]
  def getRechnungDetail(id: RechnungId)(implicit session: DBSession, cpContext: ConnectionPoolContext): Option[RechnungDetail]
  def getRechnungByReferenznummer(referenzNummer: String)(implicit session: DBSession, cpContext: ConnectionPoolContext): Option[Rechnung]

  def getZahlungsImports(implicit session: DBSession, cpContext: ConnectionPoolContext): List[ZahlungsImport]
  def getZahlungsImportDetail(id: ZahlungsImportId)(implicit session: DBSession, cpContext: ConnectionPoolContext): Option[ZahlungsImportDetail]
}

class BuchhaltungReadRepositoryImpl extends BuchhaltungReadRepository with LazyLogging with BuchhaltungRepositoryQueries {
  def getRechnungen(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Rechnung]] = {
    getRechnungenQuery.future
  }

  def getKundenRechnungen(kundeId: KundeId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Rechnung]] = {
    getKundenRechnungenQuery(kundeId).future
  }

  def getRechnungDetail(id: RechnungId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[RechnungDetail]] = {
    getRechnungDetailQuery(id).future
  }

  def getRechnungByReferenznummer(referenzNummer: String)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[Rechnung]] = {
    getRechnungByReferenznummerQuery(referenzNummer).future
  }

  def getZahlungsImports(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[ZahlungsImport]] = {
    getZahlungsImportsQuery.future
  }

  def getZahlungsImportDetail(id: ZahlungsImportId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[ZahlungsImportDetail]] = {
    getZahlungsImportDetailQuery(id).future
  }
}

class BuchhaltungWriteRepositoryImpl(val system: ActorSystem) extends BuchhaltungWriteRepository with LazyLogging with AkkaEventStream with BuchhaltungRepositoryQueries {
  override def cleanupDatabase(implicit cpContext: ConnectionPoolContext) = {
    DB localTx { implicit session =>
      sql"truncate table ${rechnungMapping.table}".execute.apply()
      sql"truncate table ${zahlungsImportMapping.table}".execute.apply()
      sql"truncate table ${zahlungsEingangMapping.table}".execute.apply()
    }
  }

  def getRechnungen(implicit session: DBSession, cpContext: ConnectionPoolContext): List[Rechnung] = {
    getRechnungenQuery.apply()
  }

  def getKundenRechnungen(kundeId: KundeId)(implicit session: DBSession, cpContext: ConnectionPoolContext): List[Rechnung] = {
    getKundenRechnungenQuery(kundeId).apply()
  }

  def getRechnungDetail(id: RechnungId)(implicit session: DBSession, cpContext: ConnectionPoolContext): Option[RechnungDetail] = {
    getRechnungDetailQuery(id).apply()
  }

  def getRechnungByReferenznummer(referenzNummer: String)(implicit session: DBSession, cpContext: ConnectionPoolContext): Option[Rechnung] = {
    getRechnungByReferenznummerQuery(referenzNummer).apply()
  }

  def getZahlungsImports(implicit session: DBSession, cpContext: ConnectionPoolContext): List[ZahlungsImport] = {
    getZahlungsImportsQuery.apply()
  }

  def getZahlungsImportDetail(id: ZahlungsImportId)(implicit session: DBSession, cpContext: ConnectionPoolContext): Option[ZahlungsImportDetail] = {
    getZahlungsImportDetailQuery(id).apply()
  }
}