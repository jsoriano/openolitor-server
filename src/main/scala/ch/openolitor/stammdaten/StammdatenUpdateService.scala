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

import ch.openolitor.core._
import ch.openolitor.core.db._
import ch.openolitor.core.domain._
import scala.concurrent.duration._
import ch.openolitor.stammdaten._
import ch.openolitor.stammdaten.dto._
import scalikejdbc.DB
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.core.domain.EntityStore._

object StammdatenUpdateService {
  def apply(implicit sysConfig: SystemConfig): StammdatenUpdateService = new DefaultStammdatenUpdateService(sysConfig)
}

class DefaultStammdatenUpdateService(sysConfig: SystemConfig)
  extends StammdatenUpdateService(sysConfig) with DefaultStammdatenRepositoryComponent {
}

/**
 * Actor zum Verarbeiten der Update Anweisungen innerhalb des Stammdaten Moduls
 */
class StammdatenUpdateService(override val sysConfig: SystemConfig) extends EventService[EntityUpdatedEvent] with LazyLogging with ConnectionPoolContextAware {
  self: StammdatenRepositoryComponent =>

  val handle: Handle = {
    case EntityUpdatedEvent(meta, entity: AbotypDetail) =>
      updateAbotyp(entity)
    case EntityUpdatedEvent(meta, entity) =>
      logger.debug(s"Receive unmatched update event for entity:$entity")
    case e =>
      logger.warn(s"Unknown event:$e")
  }

  def updateAbotyp(abotyp: AbotypDetail) = {
    val typ = Abotyp(abotyp.id, abotyp.name, abotyp.beschreibung, abotyp.lieferrhythmus, abotyp.enddatum, abotyp.anzahlLieferungen, abotyp.anzahlAbwesenheiten,
      abotyp.preis, abotyp.preiseinheit, abotyp.aktiv, abotyp.anzahlAbonnenten, abotyp.letzteLieferung)
    DB autoCommit { implicit session =>
      //create abotyp
      writeRepository.updateEntity(typ)

      //TODO: update vertriebsarten mapping
      abotyp.vertriebsarten.map { vertriebsart =>
        //TODO: insert
      }
    }
  }
}