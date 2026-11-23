package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.ProjectFabricEntity
import at.mtr.sewfar.data.db.ProjectPatternEntity
import at.mtr.sewfar.domain.CoverCandidate
import at.mtr.sewfar.domain.CoverRules
import at.mtr.sewfar.domain.PatternRef
import at.mtr.sewfar.domain.ProjectSummary
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Joins projects with persons, patterns, fabrics and photos into [ProjectSummary]s (SPEC 3.1). */
object ProjectSummaries {
    fun build(
        projects: List<ProjectEntity>,
        persons: List<PersonEntity>,
        patterns: List<PatternEntity>,
        links: List<ProjectPatternEntity>,
        fabrics: List<ProjectFabricEntity>,
        photos: List<PhotoEntity>,
        zone: ZoneId,
        personPhotos: List<PhotoEntity> = emptyList(),
    ): List<ProjectSummary> {
        val personById = persons.associateBy { it.id }
        val patternById = patterns.associateBy { it.id }
        val linksByProject = links.groupBy { it.projectId }
        val fabricsByProject = fabrics.groupBy { it.projectId }
        val photosByProject = photos.groupBy { it.ownerId }
        val personPhotoByPerson = personPhotos.associateBy({ it.ownerId }, { it.id })
        return projects.map { p ->
            val person = p.personId?.let(personById::get)
            ProjectSummary(
                id = p.id, name = p.name, type = p.type, status = p.status,
                personId = person?.id, personName = person?.name, personColor = person?.avatarColor,
                personPhotoId = person?.id?.let(personPhotoByPerson::get),
                patterns = linksByProject[p.id].orEmpty().mapNotNull { link ->
                    patternById[link.patternId]?.let { PatternRef(it.id, it.name, it.designer, link.sizeSewn) }
                },
                size = p.size, notes = p.notes,
                fabrics = fabricsByProject[p.id].orEmpty().map { it.description },
                startedOn = p.startedOn, finishedOn = p.finishedOn,
                createdOn = Instant.ofEpochMilli(p.createdAt).atZone(zone).toLocalDate(),
                lastActivityAt = p.lastActivityAt,
                coverPhotoId = CoverRules.cover(
                    photosByProject[p.id].orEmpty().map { CoverCandidate(it.id, it.tag, it.sortOrder) },
                    p.coverPhotoId,
                ),
            )
        }
    }

    /** Live summaries of all live projects; persons and patterns include archived ones (still shown on projects). */
    fun observe(
        projects: ProjectRepository,
        persons: PersonRepository,
        patterns: PatternRepository,
        photos: PhotoRepository,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<ProjectSummary>> {
        val people = combine(
            persons.observe(includeArchived = true), patterns.observe(includeArchived = true),
            photos.observeAllOfType(at.mtr.sewfar.domain.PhotoOwner.PERSON),
        ) { a, b, c -> Triple(a, b, c) }
        val children = combine(
            projects.observeAllPatternLinks(),
            projects.observeAllFabrics(),
            photos.observeAllOfType(at.mtr.sewfar.domain.PhotoOwner.PROJECT),
        ) { links, fabrics, projectPhotos -> Triple(links, fabrics, projectPhotos) }
        return combine(projects.observeAll(), people, children) { all, (personList, patternList, personPhotos), (links, fabrics, projectPhotos) ->
            build(all, personList, patternList, links, fabrics, projectPhotos, zone, personPhotos)
        }
    }
}
