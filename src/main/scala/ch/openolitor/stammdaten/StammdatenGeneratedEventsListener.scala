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
package ch.openolitor.stammdaten

import akka.actor._
import ch.openolitor.core.SystemConfig
import ch.openolitor.core.mailservice.MailService._
import ch.openolitor.stammdaten.models._
import ch.openolitor.stammdaten.repositories._
import ch.openolitor.core.domain._
import ch.openolitor.core.db._
import ch.openolitor.core.models.PersonId
import scalikejdbc._
import ch.openolitor.stammdaten.StammdatenCommandHandler.AboAktiviertEvent
import ch.openolitor.stammdaten.StammdatenCommandHandler.AboDeaktiviertEvent
import ch.openolitor.core.models.BaseEntity
import ch.openolitor.core.models.BaseId
import ch.openolitor.core.repositories.BaseEntitySQLSyntaxSupport
import ch.openolitor.core.repositories.SqlBinder

object StammdatenGeneratedEventsListener {
  def props(implicit sysConfig: SystemConfig, system: ActorSystem): Props = Props(classOf[DefaultStammdatenGeneratedEventsListener], sysConfig, system)
}

class DefaultStammdatenGeneratedEventsListener(sysConfig: SystemConfig, override val system: ActorSystem) extends StammdatenGeneratedEventsListener(sysConfig) with DefaultStammdatenWriteRepositoryComponent

/**
 * Listens to succesful sent mails
 */
class StammdatenGeneratedEventsListener(override val sysConfig: SystemConfig) extends Actor with ActorLogging
    with StammdatenDBMappings
    with ConnectionPoolContextAware {
  this: StammdatenWriteRepositoryComponent =>
  import StammdatenGeneratedEventsListener._

  override def preStart() {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[PersistentGeneratedEvent])
  }

  override def postStop() {
    context.system.eventStream.unsubscribe(self, classOf[PersistentGeneratedEvent])
    super.postStop()
  }

  def receive: Receive = {
    case AboAktiviertEvent(meta, id: AboId) =>
      handleAboAktiviert(meta, id)
    case AboDeaktiviertEvent(meta, id: AboId) =>
      handleAboDeaktiviert(meta, id)
    case _ =>
    // nothing to handle
  }

  def handleAboAktiviert(meta: EventMetadata, id: AboId)(implicit personId: PersonId = meta.originator) = {
    handleChange(id, 1)
  }

  def handleAboDeaktiviert(meta: EventMetadata, id: AboId)(implicit personId: PersonId = meta.originator) = {
    handleChange(id, -1)
  }

  private def handleChange(id: AboId, change: Int)(implicit personId: PersonId) = {
    DB autoCommit { implicit session =>
      stammdatenWriteRepository.getAboDetail(id) map { abo =>

        abo match {
          case d: DepotlieferungAboDetail =>
            modifyEntity[Depot, DepotId](d.depotId, { depot =>
              depot.copy(anzahlAbonnentenAktiv = depot.anzahlAbonnentenAktiv + change)
            })
          case h: HeimlieferungAboDetail =>
            modifyEntity[Tour, TourId](h.tourId, { tour =>
              tour.copy(anzahlAbonnentenAktiv = tour.anzahlAbonnentenAktiv + change)
            })
          case _ =>
          // nothing to change
        }

        modifyEntity[Abotyp, AbotypId](abo.abotypId, { abotyp =>
          abotyp.copy(anzahlAbonnentenAktiv = abotyp.anzahlAbonnentenAktiv + change)
        })
        modifyEntity[Kunde, KundeId](abo.kundeId, { kunde =>
          kunde.copy(anzahlAbosAktiv = kunde.anzahlAbosAktiv + change)
        })
        modifyEntity[Vertrieb, VertriebId](abo.vertriebId, { vertrieb =>
          vertrieb.copy(anzahlAbosAktiv = vertrieb.anzahlAbosAktiv + change)
        })
        modifyEntity[Depotlieferung, VertriebsartId](abo.vertriebsartId, { vertriebsart =>
          vertriebsart.copy(anzahlAbosAktiv = vertriebsart.anzahlAbosAktiv + change)
        })
        modifyEntity[Heimlieferung, VertriebsartId](abo.vertriebsartId, { vertriebsart =>
          vertriebsart.copy(anzahlAbosAktiv = vertriebsart.anzahlAbosAktiv + change)
        })
        modifyEntity[Postlieferung, VertriebsartId](abo.vertriebsartId, { vertriebsart =>
          vertriebsart.copy(anzahlAbosAktiv = vertriebsart.anzahlAbosAktiv + change)
        })
      }
    }
  }

  // TODO refactor this further
  def modifyEntity[E <: BaseEntity[I], I <: BaseId](
    id: I, mod: E => E
  )(implicit session: DBSession, syntax: BaseEntitySQLSyntaxSupport[E], binder: SqlBinder[I], personId: PersonId): Option[E] = {
    modifyEntityWithRepository(stammdatenWriteRepository)(id, mod)
  }
}