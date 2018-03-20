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
package ch.openolitor.arbeitseinsatz

import akka.actor._
import ch.openolitor.arbeitseinsatz.models._
import ch.openolitor.arbeitseinsatz.repositories._
import ch.openolitor.core._
import ch.openolitor.core.db._
import ch.openolitor.core.domain.EntityStore._
import ch.openolitor.core.domain._
import ch.openolitor.core.models._
import ch.openolitor.core.repositories.EventPublishingImplicits._
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.DB

object ArbeitseinsatzDeleteService {
  def apply(implicit sysConfig: SystemConfig, system: ActorSystem): ArbeitseinsatzDeleteService = new DefaultArbeitseinsatzDeleteService(sysConfig, system)
}

class DefaultArbeitseinsatzDeleteService(sysConfig: SystemConfig, override val system: ActorSystem)
  extends ArbeitseinsatzDeleteService(sysConfig: SystemConfig) with DefaultArbeitseinsatzWriteRepositoryComponent {
}

/**
 * Actor zum Verarbeiten der Delete Anweisungen für das Arbeitseinsatz Modul
 */
class ArbeitseinsatzDeleteService(override val sysConfig: SystemConfig) extends EventService[EntityDeletedEvent[_]]
  with LazyLogging with AsyncConnectionPoolContextAware with ArbeitseinsatzDBMappings {
  self: ArbeitseinsatzWriteRepositoryComponent =>
  import EntityStore._

  val ZERO = 0

  val handle: Handle = {
    case EntityDeletedEvent(meta, id: ArbeitskategorieId) => deleteArbeitskategorie(meta, id)
    case EntityDeletedEvent(meta, id: ArbeitsangebotId) => deleteArbeitsangebot(meta, id)
    case EntityDeletedEvent(meta, id: ArbeitseinsatzId) => delteArbeitseinsatz(meta, id)
    case e =>
  }

  def deleteArbeitskategorie(meta: EventMetadata, id: ArbeitskategorieId)(implicit personId: PersonId = meta.originator) = {
    DB localTxPostPublish { implicit session => implicit publisher =>
      arbeitseinsatzWriteRepository.deleteEntity[Arbeitskategorie, ArbeitskategorieId](id)
    }
  }

  def deleteArbeitsangebot(meta: EventMetadata, id: ArbeitsangebotId)(implicit personId: PersonId = meta.originator) = {
    DB localTxPostPublish { implicit session => implicit publisher =>
      arbeitseinsatzWriteRepository.deleteEntity[Arbeitsangebot, ArbeitsangebotId](id, { arbeitsangebot: Arbeitsangebot => arbeitsangebot.status == ArbeitseinsatzStatus })
    }
  }

  def delteArbeitseinsatz(meta: EventMetadata, id: ArbeitseinsatzId)(implicit personId: PersonId = meta.originator) = {
    DB localTxPostPublish { implicit session => implicit publisher =>
      arbeitseinsatzWriteRepository.deleteEntity[Arbeitseinsatz, ArbeitseinsatzId](id)
    }
  }
}
