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

import ch.openolitor.core.domain._
import ch.openolitor.core.models._
import scala.util._
import scalikejdbc.DB
import ch.openolitor.stammdaten.models._
import ch.openolitor.stammdaten.repositories._
import ch.openolitor.core.exceptions._
import akka.actor.ActorSystem
import ch.openolitor.core._
import ch.openolitor.core.db.ConnectionPoolContextAware
import ch.openolitor.core.Macros._
import com.fasterxml.jackson.databind.JsonSerializable
import ch.openolitor.buchhaltung.models.RechnungCreate
import ch.openolitor.buchhaltung.models.RechnungId
import org.joda.time.DateTime
import java.util.UUID
import scalikejdbc.DBSession
import ch.openolitor.util.IdUtil

object StammdatenCommandHandler {
  case class LieferplanungAbschliessenCommand(originator: PersonId, id: LieferplanungId) extends UserCommand
  case class LieferplanungModifyCommand(originator: PersonId, lieferplanungModify: LieferplanungPositionenModify) extends UserCommand
  case class LieferplanungAbrechnenCommand(originator: PersonId, id: LieferplanungId) extends UserCommand
  case class AbwesenheitCreateCommand(originator: PersonId, abw: AbwesenheitCreate) extends UserCommand
  case class SammelbestellungAnProduzentenVersendenCommand(originator: PersonId, id: SammelbestellungId) extends UserCommand
  case class PasswortWechselCommand(originator: PersonId, personId: PersonId, passwort: Array[Char], einladung: Option[EinladungId]) extends UserCommand
  case class AuslieferungenAlsAusgeliefertMarkierenCommand(originator: PersonId, ids: Seq[AuslieferungId]) extends UserCommand
  case class CreateAnzahlLieferungenRechnungenCommand(originator: PersonId, aboRechnungCreate: AboRechnungCreate) extends UserCommand
  case class CreateBisGuthabenRechnungenCommand(originator: PersonId, aboRechnungCreate: AboRechnungCreate) extends UserCommand
  case class LoginDeaktivierenCommand(originator: PersonId, kundeId: KundeId, personId: PersonId) extends UserCommand
  case class LoginAktivierenCommand(originator: PersonId, kundeId: KundeId, personId: PersonId) extends UserCommand
  case class EinladungSendenCommand(originator: PersonId, kundeId: KundeId, personId: PersonId) extends UserCommand
  case class SammelbestellungenAlsAbgerechnetMarkierenCommand(originator: PersonId, datum: DateTime, ids: Seq[SammelbestellungId]) extends UserCommand
  case class PasswortResetCommand(originator: PersonId, personId: PersonId) extends UserCommand
  case class RolleWechselnCommand(originator: PersonId, kundeId: KundeId, personId: PersonId, rolle: Rolle) extends UserCommand

  // TODO person id for calculations
  case class AboAktivierenCommand(aboId: AboId, originator: PersonId = PersonId(100)) extends UserCommand
  case class AboDeaktivierenCommand(aboId: AboId, originator: PersonId = PersonId(100)) extends UserCommand

  case class DeleteAbwesenheitCommand(originator: PersonId, id: AbwesenheitId) extends UserCommand

  case class LieferplanungAbschliessenEvent(meta: EventMetadata, id: LieferplanungId) extends PersistentEvent with JSONSerializable
  case class LieferplanungAbrechnenEvent(meta: EventMetadata, id: LieferplanungId) extends PersistentEvent with JSONSerializable
  case class LieferplanungDataModifiedEvent(meta: EventMetadata, result: LieferplanungDataModify) extends PersistentEvent with JSONSerializable
  case class AbwesenheitCreateEvent(meta: EventMetadata, id: AbwesenheitId, abw: AbwesenheitCreate) extends PersistentEvent with JSONSerializable
  @Deprecated
  case class BestellungVersendenEvent(meta: EventMetadata, id: BestellungId) extends PersistentEvent with JSONSerializable
  case class SammelbestellungVersendenEvent(meta: EventMetadata, id: SammelbestellungId) extends PersistentEvent with JSONSerializable
  case class PasswortGewechseltEvent(meta: EventMetadata, personId: PersonId, passwort: Array[Char], einladungId: Option[EinladungId]) extends PersistentEvent with JSONSerializable
  case class LoginDeaktiviertEvent(meta: EventMetadata, kundeId: KundeId, personId: PersonId) extends PersistentEvent with JSONSerializable
  case class LoginAktiviertEvent(meta: EventMetadata, kundeId: KundeId, personId: PersonId) extends PersistentEvent with JSONSerializable
  case class EinladungGesendetEvent(meta: EventMetadata, einladung: EinladungCreate) extends PersistentEvent with JSONSerializable
  case class AuslieferungAlsAusgeliefertMarkierenEvent(meta: EventMetadata, id: AuslieferungId) extends PersistentEvent with JSONSerializable
  @Deprecated
  case class BestellungAlsAbgerechnetMarkierenEvent(meta: EventMetadata, datum: DateTime, id: BestellungId) extends PersistentEvent with JSONSerializable
  case class SammelbestellungAlsAbgerechnetMarkierenEvent(meta: EventMetadata, datum: DateTime, id: SammelbestellungId) extends PersistentEvent with JSONSerializable
  case class PasswortResetGesendetEvent(meta: EventMetadata, einladung: EinladungCreate) extends PersistentEvent with JSONSerializable
  case class RolleGewechseltEvent(meta: EventMetadata, kundeId: KundeId, personId: PersonId, rolle: Rolle) extends PersistentEvent with JSONSerializable

  case class AboAktiviertEvent(meta: EventMetadata, aboId: AboId) extends PersistentGeneratedEvent with JSONSerializable
  case class AboDeaktiviertEvent(meta: EventMetadata, aboId: AboId) extends PersistentGeneratedEvent with JSONSerializable
}

trait StammdatenCommandHandler extends CommandHandler with StammdatenDBMappings with ConnectionPoolContextAware {
  self: StammdatenWriteRepositoryComponent =>
  import StammdatenCommandHandler._
  import EntityStore._

  override val handle: PartialFunction[UserCommand, EventFactory => EventTransactionMetadata => Try[Seq[PersistentEvent]]] = {

    case DeleteAbwesenheitCommand(personId, id) => factory => meta =>
      DB readOnly { implicit session =>
        stammdatenWriteRepository.getLieferung(id) map { lieferung =>
          lieferung.status match {
            case (Offen | Ungeplant) =>
              Success(Seq(EntityDeletedEvent(factory.newMetadata(meta), id)))
            case _ =>
              Failure(new InvalidStateException("Die der Abwesenheit zugeordnete Lieferung muss Offen oder Ungeplant sein."))
          }
        } getOrElse (Failure(new InvalidStateException(s"Keine Lieferung zu Abwesenheit Nr. $id gefunden")))
      }

    case LieferplanungAbschliessenCommand(personId, id) => factory => meta =>
      DB readOnly { implicit session =>
        stammdatenWriteRepository.getById(lieferplanungMapping, id) map { lieferplanung =>
          lieferplanung.status match {
            case Offen =>
              stammdatenWriteRepository.countEarlierLieferungOffen(id) match {
                case Some(0) =>
                  val distinctSammelbestellungen = getDistinctSammelbestellungModifyByLieferplan(lieferplanung.id)

                  val bestellEvents = distinctSammelbestellungen.map { sammelbestellungCreate =>
                    val sammelbestellungId = SammelbestellungId(factory.newId(classOf[SammelbestellungId]))
                    val insertEvent = EntityInsertedEvent(factory.newMetadata(meta), sammelbestellungId, sammelbestellungCreate)

                    val bestellungVersendenEvent = SammelbestellungVersendenEvent(factory.newMetadata(meta), sammelbestellungId)

                    Seq(insertEvent, bestellungVersendenEvent)
                  }.toSeq.flatten

                  val lpAbschliessenEvent = LieferplanungAbschliessenEvent(factory.newMetadata(meta), id)

                  Success(lpAbschliessenEvent +: bestellEvents)
                case _ =>
                  Failure(new InvalidStateException("Es dürfen keine früheren Lieferungen in offnen Lieferplanungen hängig sein."))
              }
            case _ =>
              Failure(new InvalidStateException("Eine Lieferplanung kann nur im Status 'Offen' abgeschlossen werden"))
          }
        } getOrElse (Failure(new InvalidStateException(s"Keine Lieferplanung mit der Nr. $id gefunden")))
      }

    case LieferplanungModifyCommand(personId, lieferplanungPositionenModify) => factory => meta =>
      DB readOnly { implicit session =>
        stammdatenWriteRepository.getById(lieferplanungMapping, lieferplanungPositionenModify.id) map { lieferplanung =>
          lieferplanung.status match {
            case state @ (Offen | Abgeschlossen) =>
              stammdatenWriteRepository.countEarlierLieferungOffen(lieferplanungPositionenModify.id) match {
                case Some(0) =>
                  val missingSammelbestellungen = if (state == Abgeschlossen) {
                    // get existing sammelbestellungen
                    val existingSammelbestellungen = (stammdatenWriteRepository.getSammelbestellungen(lieferplanungPositionenModify.id) map { sammelbestellung =>
                      SammelbestellungModify(sammelbestellung.produzentId, lieferplanung.id, sammelbestellung.datum)
                    }).toSet

                    // get distinct sammelbestellungen by lieferplanung
                    val distinctSammelbestellungen = getDistinctSammelbestellungModifyByLieferplan(lieferplanung.id)

                    // evaluate which sammelbestellungen are missing and have to be inserted
                    // they will be used in handleLieferungChanged afterwards
                    (distinctSammelbestellungen -- existingSammelbestellungen).map { s =>
                      val sammelbestellungId = SammelbestellungId(factory.newId(classOf[SammelbestellungId]))
                      SammelbestellungCreate(sammelbestellungId, s.produzentId, s.lieferplanungId, s.datum)
                    }.toSeq
                  } else {
                    Nil
                  }

                  Success(LieferplanungDataModifiedEvent(factory.newMetadata(meta), LieferplanungDataModify(lieferplanungPositionenModify.id, missingSammelbestellungen.toSet, lieferplanungPositionenModify.lieferungen)) :: Nil)
                case _ =>
                  Failure(new InvalidStateException("Es dürfen keine früheren Lieferungen in offnen Lieferplanungen hängig sein."))
              }
            case _ =>
              Failure(new InvalidStateException("Eine Lieferplanung kann nur im Status 'Offen' oder 'Abgeschlossen' aktualisiert werden"))
          }
        } getOrElse (Failure(new InvalidStateException(s"Keine Lieferplanung mit der Nr. ${lieferplanungPositionenModify.id} gefunden")))
      }

    case LieferplanungAbrechnenCommand(personId, id: LieferplanungId) => factory => meta =>
      DB readOnly { implicit session =>
        stammdatenWriteRepository.getById(lieferplanungMapping, id) map { lieferplanung =>
          lieferplanung.status match {
            case Abgeschlossen =>
              Success(Seq(LieferplanungAbrechnenEvent(factory.newMetadata(meta), id)))
            case _ =>
              Failure(new InvalidStateException("Eine Lieferplanung kann nur im Status 'Abgeschlossen' verrechnet werden"))
          }
        } getOrElse (Failure(new InvalidStateException(s"Keine Lieferplanung mit der Nr. $id gefunden")))
      }

    case AbwesenheitCreateCommand(personId, abw: AbwesenheitCreate) => factory => meta =>
      DB readOnly { implicit session =>
        stammdatenWriteRepository.countAbwesend(abw.lieferungId, abw.aboId) match {
          case Some(0) =>
            handleEntityInsert[AbwesenheitCreate, AbwesenheitId](factory, meta, abw, AbwesenheitId.apply)
          case _ =>
            Failure(new InvalidStateException("Eine Abwesenheit kann nur einmal erfasst werden"))
        }
      }

    case SammelbestellungAnProduzentenVersendenCommand(personId, id: SammelbestellungId) => factory => meta =>
      DB readOnly { implicit session =>
        stammdatenWriteRepository.getById(sammelbestellungMapping, id) map { sammelbestellung =>
          sammelbestellung.status match {
            case Offen | Abgeschlossen =>
              Success(Seq(SammelbestellungVersendenEvent(factory.newMetadata(meta), id)))
            case _ =>
              Failure(new InvalidStateException("Eine Bestellung kann nur in den Status 'Offen' oder 'Abgeschlossen' versendet werden"))
          }
        } getOrElse (Failure(new InvalidStateException(s"Keine Bestellung mit der Nr. $id gefunden")))
      }

    case AuslieferungenAlsAusgeliefertMarkierenCommand(personId, ids: Seq[AuslieferungId]) => factory => meta =>
      DB readOnly { implicit session =>
        val (events, failures) = ids map { id =>
          stammdatenWriteRepository.getById(depotAuslieferungMapping, id) orElse
            stammdatenWriteRepository.getById(tourAuslieferungMapping, id) orElse
            stammdatenWriteRepository.getById(postAuslieferungMapping, id) map { auslieferung =>
              auslieferung.status match {
                case Erfasst =>
                  val copy = auslieferung match {
                    case d: DepotAuslieferung =>
                      d.copy(status = Ausgeliefert)
                    case t: TourAuslieferung =>
                      t.copy(status = Ausgeliefert)
                    case p: PostAuslieferung =>
                      p.copy(status = Ausgeliefert)
                  }
                  Success(EntityUpdatedEvent(factory.newMetadata(meta), id, copy))
                case _ =>
                  Failure(new InvalidStateException(s"Eine Auslieferung kann nur im Status 'Erfasst' als ausgeliefert markiert werden. Nr. $id"))
              }
            } getOrElse (Failure(new InvalidStateException(s"Keine Auslieferung mit der Nr. $id gefunden")))
        } partition (_.isSuccess)

        if (events.isEmpty) {
          Failure(new InvalidStateException(s"Keine der Auslieferungen konnte abgearbeitet werden"))
        } else {
          Success(events map (_.get))
        }
      }

    case SammelbestellungenAlsAbgerechnetMarkierenCommand(personId, datum, ids: Seq[SammelbestellungId]) => factory => meta =>
      DB readOnly { implicit session =>
        val (events, failures) = ids map { id =>
          stammdatenWriteRepository.getById(sammelbestellungMapping, id) map { sammelbestellung =>
            sammelbestellung.status match {
              case Abgeschlossen =>
                Success(SammelbestellungAlsAbgerechnetMarkierenEvent(factory.newMetadata(meta), datum, id))
              case _ =>
                Failure(new InvalidStateException(s"Eine Sammelbestellung kann nur im Status 'Abgeschlossen' als abgerechnet markiert werden. Nr. $id"))
            }
          } getOrElse (Failure(new InvalidStateException(s"Keine Sammelbestellung mit der Nr. $id gefunden")))
        } partition (_.isSuccess)

        if (events.isEmpty) {
          Failure(new InvalidStateException(s"Keine der Sammelbestellung konnte abgearbeitet werden"))
        } else {
          Success(events map (_.get))
        }
      }

    case CreateAnzahlLieferungenRechnungenCommand(originator, aboRechnungCreate) => factory => meta =>
      createAboRechnungen(factory, meta, aboRechnungCreate)

    case CreateBisGuthabenRechnungenCommand(originator, aboRechnungCreate) => factory => meta =>
      createAboRechnungen(factory, meta, aboRechnungCreate)

    case PasswortWechselCommand(originator, personId, pwd, einladungId) => factory => meta =>
      Success(Seq(PasswortGewechseltEvent(factory.newMetadata(meta), personId, pwd, einladungId)))

    case LoginDeaktivierenCommand(originator, kundeId, personId) if originator.id != personId => factory => meta =>
      Success(Seq(LoginDeaktiviertEvent(factory.newMetadata(meta), kundeId, personId)))

    case LoginAktivierenCommand(originator, kundeId, personId) if originator.id != personId => factory => meta =>
      Success(Seq(LoginAktiviertEvent(factory.newMetadata(meta), kundeId, personId)))

    case EinladungSendenCommand(originator, kundeId, personId) if originator.id != personId => factory => meta =>
      sendEinladung(factory, meta, kundeId, personId)

    case PasswortResetCommand(originator, personId) => factory => meta =>
      sendPasswortReset(factory, meta, personId)

    case RolleWechselnCommand(originator, kundeId, personId, rolle) if originator.id != personId => factory => meta =>
      changeRolle(factory, meta, kundeId, personId, rolle)

    case AboAktivierenCommand(aboId, originator) => factory => meta =>
      Success(Seq(AboAktiviertEvent(factory.newMetadata(meta), aboId)))

    case AboDeaktivierenCommand(aboId, originator) => factory => meta =>
      Success(Seq(AboDeaktiviertEvent(factory.newMetadata(meta), aboId)))

    /*
       * Insert command handling
       */
    case e @ InsertEntityCommand(personId, entity: CustomKundentypCreate) => factory => meta =>
      handleEntityInsert[CustomKundentypCreate, CustomKundentypId](factory, meta, entity, CustomKundentypId.apply)
    case e @ InsertEntityCommand(personId, entity: LieferungenAbotypCreate) => factory => meta =>
      val events = entity.daten.map { datum =>
        val lieferungCreate = copyTo[LieferungenAbotypCreate, LieferungAbotypCreate](entity, "datum" -> datum)
        insertEntityEvent[LieferungAbotypCreate, LieferungId](factory, meta, lieferungCreate, LieferungId.apply)
      }
      Success(events)
    case e @ InsertEntityCommand(personId, entity: LieferungAbotypCreate) => factory => meta =>
      handleEntityInsert[LieferungAbotypCreate, LieferungId](factory, meta, entity, LieferungId.apply)
    case e @ InsertEntityCommand(personId, entity: LieferplanungCreate) => factory => meta =>
      handleEntityInsert[LieferplanungCreate, LieferplanungId](factory, meta, entity, LieferplanungId.apply)
    case e @ InsertEntityCommand(personId, entity: LieferungPlanungAdd) => factory => meta =>
      handleEntityInsert[LieferungPlanungAdd, LieferungId](factory, meta, entity, LieferungId.apply)
    case e @ InsertEntityCommand(personId, entity: LieferpositionenModify) => factory => meta =>
      handleEntityInsert[LieferpositionenModify, LieferpositionId](factory, meta, entity, LieferpositionId.apply)
    case e @ InsertEntityCommand(personId, entity: PendenzModify) => factory => meta =>
      handleEntityInsert[PendenzModify, PendenzId](factory, meta, entity, PendenzId.apply)
    case e @ InsertEntityCommand(personId, entity: PersonCreate) => factory => meta =>
      handleEntityInsert[PersonCreate, PersonId](factory, meta, entity, PersonId.apply)
    case e @ InsertEntityCommand(personId, entity: ProduzentModify) => factory => meta =>
      handleEntityInsert[ProduzentModify, ProduzentId](factory, meta, entity, ProduzentId.apply)
    case e @ InsertEntityCommand(personId, entity: ProduktModify) => factory => meta =>
      handleEntityInsert[ProduktModify, ProduktId](factory, meta, entity, ProduktId.apply)
    case e @ InsertEntityCommand(personId, entity: ProduktProduktekategorie) => factory => meta =>
      handleEntityInsert[ProduktProduktekategorie, ProduktProduktekategorieId](factory, meta, entity, ProduktProduktekategorieId.apply)
    case e @ InsertEntityCommand(personId, entity: ProduktProduzent) => factory => meta =>
      handleEntityInsert[ProduktProduzent, ProduktProduzentId](factory, meta, entity, ProduktProduzentId.apply)
    case e @ InsertEntityCommand(personId, entity: ProduktekategorieModify) => factory => meta =>
      handleEntityInsert[ProduktekategorieModify, ProduktekategorieId](factory, meta, entity, ProduktekategorieId.apply)
    case e @ InsertEntityCommand(personId, entity: ProjektModify) => factory => meta =>
      handleEntityInsert[ProjektModify, ProjektId](factory, meta, entity, ProjektId.apply)
    case e @ InsertEntityCommand(personId, entity: TourCreate) => factory => meta =>
      handleEntityInsert[TourCreate, TourId](factory, meta, entity, TourId.apply)
    case e @ InsertEntityCommand(personId, entity: AbwesenheitCreate) => factory => meta =>
      handleEntityInsert[AbwesenheitCreate, AbwesenheitId](factory, meta, entity, AbwesenheitId.apply)
    case e @ InsertEntityCommand(personId, entity: AbotypModify) => factory => meta =>
      handleEntityInsert[AbotypModify, AbotypId](factory, meta, entity, AbotypId.apply)
    case e @ InsertEntityCommand(personId, entity: DepotModify) => factory => meta =>
      handleEntityInsert[DepotModify, DepotId](factory, meta, entity, DepotId.apply)
    case e @ InsertEntityCommand(personId, entity: DepotlieferungModify) => factory => meta =>
      handleEntityInsert[DepotlieferungModify, VertriebsartId](factory, meta, entity, VertriebsartId.apply)
    case e @ InsertEntityCommand(personId, entity: HeimlieferungModify) => factory => meta =>
      handleEntityInsert[HeimlieferungModify, VertriebsartId](factory, meta, entity, VertriebsartId.apply)
    case e @ InsertEntityCommand(personId, entity: PostlieferungModify) => factory => meta =>
      handleEntityInsert[PostlieferungModify, VertriebsartId](factory, meta, entity, VertriebsartId.apply)
    case e @ InsertEntityCommand(personId, entity: DepotlieferungAbotypModify) => factory => meta =>
      handleEntityInsert[DepotlieferungAbotypModify, VertriebsartId](factory, meta, entity, VertriebsartId.apply)
    case e @ InsertEntityCommand(personId, entity: HeimlieferungAbotypModify) => factory => meta =>
      handleEntityInsert[HeimlieferungAbotypModify, VertriebsartId](factory, meta, entity, VertriebsartId.apply)
    case e @ InsertEntityCommand(personId, entity: PostlieferungAbotypModify) => factory => meta =>
      handleEntityInsert[PostlieferungAbotypModify, VertriebsartId](factory, meta, entity, VertriebsartId.apply)
    case e @ InsertEntityCommand(personId, entity: DepotlieferungAboModify) => factory => meta =>
      handleEntityInsert[DepotlieferungAboModify, AboId](factory, meta, entity, AboId.apply)
    case e @ InsertEntityCommand(personId, entity: HeimlieferungAboModify) => factory => meta =>
      handleEntityInsert[HeimlieferungAboModify, AboId](factory, meta, entity, AboId.apply)
    case e @ InsertEntityCommand(personId, entity: PostlieferungAboModify) => factory => meta =>
      handleEntityInsert[PostlieferungAboModify, AboId](factory, meta, entity, AboId.apply)
    case e @ InsertEntityCommand(personId, entity: PendenzCreate) => factory => meta =>
      handleEntityInsert[PendenzCreate, PendenzId](factory, meta, entity, PendenzId.apply)
    case e @ InsertEntityCommand(personId, entity: VertriebModify) => factory => meta =>
      handleEntityInsert[VertriebModify, VertriebId](factory, meta, entity, VertriebId.apply)
    case e @ InsertEntityCommand(personId, entity: ProjektVorlageCreate) => factory => meta =>
      handleEntityInsert[ProjektVorlageCreate, ProjektVorlageId](factory, meta, entity, ProjektVorlageId.apply)
    case e @ InsertEntityCommand(personId, entity: KundeModify) => factory => meta =>
      if (entity.ansprechpersonen.isEmpty) {
        Failure(new InvalidStateException(s"Zum Erstellen eines Kunden muss mindestens ein Ansprechpartner angegeben werden"))
      } else {
        logger.debug(s"created => Insert entity:$entity")
        val kundeEvent = EntityInsertedEvent(factory.newMetadata(meta), KundeId(factory.newId(classOf[KundeId])), entity)

        val kundeId = kundeEvent.id
        val apartnerEvents = entity.ansprechpersonen.zipWithIndex.map {
          case (newPerson, index) =>
            val sort = index + 1
            val personCreate = copyTo[PersonModify, PersonCreate](newPerson, "kundeId" -> kundeId, "sort" -> sort)
            logger.debug(s"created => Insert entity:$personCreate")
            EntityInsertedEvent(factory.newMetadata(meta), PersonId(factory.newId(classOf[PersonId])), personCreate)
        }

        Success(kundeEvent +: apartnerEvents)
      }

    /*
    * Custom update command handling
    */
    case UpdateEntityCommand(personId, id: KundeId, entity: KundeModify) => factory => meta =>
      val partitions = entity.ansprechpersonen.partition(_.id.isDefined)
      val newPersons = partitions._2.zipWithIndex.map {
        case (newPerson, index) =>
          //generate persistent id for new person
          val sort = partitions._1.length + index
          val personCreate = copyTo[PersonModify, PersonCreate](newPerson, "kundeId" -> id, "sort" -> sort)
          val event = EntityInsertedEvent(factory.newMetadata(meta), PersonId(factory.newId(classOf[PersonId])), personCreate)
          (event, newPerson.copy(id = Some(event.id)))
      }
      val newPersonsEvents = newPersons.map(_._1)
      val updatePersons = (partitions._1 ++ newPersons.map(_._2))

      val pendenzenPartitions = entity.pendenzen.partition(_.id.isDefined)
      val newPendenzen = pendenzenPartitions._2.map {
        case newPendenz =>
          val pendenzCreate = copyTo[PendenzModify, PendenzCreate](newPendenz, "kundeId" -> id, "generiert" -> FALSE)
          val event = EntityInsertedEvent(factory.newMetadata(meta), PendenzId(factory.newId(classOf[PendenzId])), pendenzCreate)
          (event, newPendenz.copy(id = Some(event.id)))
      }
      val newPendenzenEvents = newPendenzen.map(_._1)
      val updatePendenzen = (pendenzenPartitions._1 ++ newPendenzen.map(_._2))

      val updateEntity = entity.copy(ansprechpersonen = updatePersons, pendenzen = updatePendenzen)
      val updateEvent = EntityUpdatedEvent(factory.newMetadata(meta), id, updateEntity)
      Success(updateEvent +: (newPersonsEvents ++ newPendenzenEvents))

    case UpdateEntityCommand(personId, id: AboId, entity: AboGuthabenModify) => factory => meta =>
      DB readOnly { implicit session =>
        //TODO: assemble text using gettext
        stammdatenWriteRepository.getAboDetail(id) match {
          case Some(abo) => {
            val text = s"Guthaben manuell angepasst. Abo Nr.: ${id.id}; Bisher: ${abo.guthaben}; Neu: ${entity.guthabenNeu}; Grund: ${entity.bemerkung}"
            val pendenzEvent = addKundenPendenz(factory, meta, id, text)
            Success(Seq(Some(EntityUpdatedEvent(factory.newMetadata(meta), id, entity)), pendenzEvent).flatten)
          }
          case None =>
            Failure(new InvalidStateException(s"UpdateEntityCommand: Abo konnte nicht gefunden werden"))
        }
      }
    case UpdateEntityCommand(personId, id: AboId, entity: AboVertriebsartModify) => factory => meta =>
      //TODO: assemble text using gettext
      val text = s"Vertriebsart angepasst. Abo Nr.: ${id.id}, Neu: ${entity.vertriebsartIdNeu}; Grund: ${entity.bemerkung}"
      val pendenzEvent = addKundenPendenz(factory, meta, id, text)
      Success(Seq(Some(EntityUpdatedEvent(factory.newMetadata(meta), id, entity)), pendenzEvent).flatten)
  }

  def addKundenPendenz(factory: EventFactory, meta: EventTransactionMetadata, id: AboId, bemerkung: String): Option[PersistentEvent] = {
    DB readOnly { implicit session =>
      // zusätzlich eine pendenz erstellen
      ((stammdatenWriteRepository.getById(depotlieferungAboMapping, id) map { abo =>
        DepotlieferungAboModify
        abo.kundeId
      }) orElse (stammdatenWriteRepository.getById(heimlieferungAboMapping, id) map { abo =>
        abo.kundeId
      }) orElse (stammdatenWriteRepository.getById(postlieferungAboMapping, id) map { abo =>
        abo.kundeId
      })) map { kundeId =>
        //TODO: assemble text using gettext
        val title = "Guthaben angepasst: "
        val pendenzCreate = PendenzCreate(kundeId, meta.timestamp, Some(bemerkung), Erledigt, true)
        EntityInsertedEvent[PendenzId, PendenzCreate](factory.newMetadata(meta), PendenzId(factory.newId(classOf[PendenzId])), pendenzCreate)
      }
    }
  }

  def createAboRechnungen(factory: EventFactory, meta: EventTransactionMetadata, aboRechnungCreate: AboRechnungCreate) = {
    DB readOnly { implicit session =>
      val (events, failures) = aboRechnungCreate.ids map { id =>
        stammdatenWriteRepository.getAboDetail(id) flatMap { aboDetail =>
          stammdatenWriteRepository.getById(abotypMapping, aboDetail.abotypId) flatMap { abotyp =>
            stammdatenWriteRepository.getById(kundeMapping, aboDetail.kundeId) map { kunde =>

              // TODO check preisEinheit
              if (abotyp.preiseinheit != ProLieferung) {
                Failure(new InvalidStateException(s"Für den Abotyp dieses Abos ($id) kann keine Guthabenrechnung erstellt werden"))
              } else {
                // has to be refactored as soon as more modes are available
                val anzahlLieferungen = aboRechnungCreate.anzahlLieferungen getOrElse {
                  math.max(((aboRechnungCreate.bisGuthaben getOrElse aboDetail.guthaben) - aboDetail.guthaben), 0)
                }

                if (anzahlLieferungen > 0) {
                  val betrag = aboRechnungCreate.betrag getOrElse abotyp.preis * anzahlLieferungen

                  val rechnung = RechnungCreate(
                    aboDetail.kundeId,
                    id,
                    aboRechnungCreate.titel,
                    anzahlLieferungen,
                    aboRechnungCreate.waehrung,
                    betrag,
                    None,
                    aboRechnungCreate.rechnungsDatum,
                    aboRechnungCreate.faelligkeitsDatum,
                    None,
                    kunde.strasseLieferung getOrElse kunde.strasse,
                    kunde.hausNummerLieferung orElse kunde.hausNummer,
                    kunde.adressZusatzLieferung orElse kunde.adressZusatz,
                    kunde.plzLieferung getOrElse kunde.plz,
                    kunde.ortLieferung getOrElse kunde.ort
                  )

                  Success(insertEntityEvent(factory, meta, rechnung, RechnungId.apply))
                } else {
                  Failure(new InvalidStateException(s"Für das Abo mit der Id $id wurde keine Rechnung erstellt. Anzahl Lieferungen 0"))
                }
              }
            }
          }
        } getOrElse (Failure(new InvalidStateException(s"Für das Abo mit der Id $id konnte keine Rechnung erstellt werden.")))
      } partition (_.isSuccess)

      if (events.isEmpty) {
        Failure(new InvalidStateException(s"Keine der Rechnungen konnte erstellt werden"))
      } else {
        Success(events map (_.get))
      }
    }
  }

  def sendEinladung(factory: EventFactory, meta: EventTransactionMetadata, kundeId: KundeId, personId: PersonId) = {
    DB readOnly { implicit session =>
      stammdatenWriteRepository.getById(personMapping, personId) map { person =>
        person.email map { email =>
          val id = EinladungId(factory.newId(classOf[EinladungId]))

          val einladung = EinladungCreate(
            id,
            personId,
            UUID.randomUUID.toString,
            DateTime.now.plusDays(90),
            None
          )

          Success(Seq(EinladungGesendetEvent(factory.newMetadata(meta), einladung)))
        } getOrElse {
          Failure(new InvalidStateException(s"Dieser Person kann keine Einladung gesendet werden da sie keine Emailadresse besitzt."))
        }
      } getOrElse {
        Failure(new InvalidStateException(s"Person wurde nicht gefunden."))
      }
    }
  }

  def sendPasswortReset(factory: EventFactory, meta: EventTransactionMetadata, personId: PersonId) = {
    DB readOnly { implicit session =>
      stammdatenWriteRepository.getById(personMapping, personId) map { person =>
        person.email map { email =>
          val id = EinladungId(factory.newId(classOf[EinladungId]))

          val einladung = EinladungCreate(
            id,
            personId,
            UUID.randomUUID.toString,
            DateTime.now.plusMinutes(120),
            None
          )

          Success(Seq(PasswortResetGesendetEvent(factory.newMetadata(meta), einladung)))
        } getOrElse {
          Failure(new InvalidStateException(s"Dieser Person kann keine Einladung gesendet werden da sie keine Emailadresse besitzt."))
        }
      } getOrElse {
        Failure(new InvalidStateException(s"Person wurde nicht gefunden."))
      }
    }
  }

  def changeRolle(factory: EventFactory, meta: EventTransactionMetadata, kundeId: KundeId, personId: PersonId, rolle: Rolle) = {
    DB readOnly { implicit session =>
      stammdatenWriteRepository.getById(personMapping, personId) map { person =>
        person.rolle map { existingRolle =>
          if (existingRolle != rolle) {
            Success(Seq(RolleGewechseltEvent(factory.newMetadata(meta), kundeId, personId, rolle)))
          } else {
            Failure(new InvalidStateException(s"Die Person mit der Id: $personId hat bereits die Rolle: $rolle."))
          }
        } getOrElse {
          Success(Seq(RolleGewechseltEvent(factory.newMetadata(meta), kundeId, personId, rolle)))
        }
      } getOrElse {
        Failure(new InvalidStateException(s"Person wurde nicht gefunden."))
      }
    }
  }

  private def getDistinctSammelbestellungModifyByLieferplan(lieferplanungId: LieferplanungId)(implicit session: DBSession): Set[SammelbestellungModify] = {
    stammdatenWriteRepository.getLieferpositionenByLieferplan(lieferplanungId).map { lieferposition =>
      stammdatenWriteRepository.getById(lieferungMapping, lieferposition.lieferungId).map { lieferung =>
        SammelbestellungModify(lieferposition.produzentId, lieferplanungId, lieferung.datum)
      }
    }.flatten.toSet
  }
}

class DefaultStammdatenCommandHandler(override val sysConfig: SystemConfig, override val system: ActorSystem) extends StammdatenCommandHandler
    with DefaultStammdatenWriteRepositoryComponent {
}
