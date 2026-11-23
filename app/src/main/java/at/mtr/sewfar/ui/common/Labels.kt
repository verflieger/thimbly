package at.mtr.sewfar.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.ui.graphics.vector.ImageVector
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType

@get:StringRes
val ProjectStatus.label: Int
    get() = when (this) {
        ProjectStatus.IDEA -> R.string.status_idea
        ProjectStatus.PLANNED -> R.string.status_planned
        ProjectStatus.IN_PROGRESS -> R.string.status_in_progress
        ProjectStatus.PAUSED -> R.string.status_paused
        ProjectStatus.DONE -> R.string.status_done
    }

val ProjectStatus.icon: ImageVector
    get() = when (this) {
        ProjectStatus.IDEA -> Icons.Outlined.Lightbulb
        ProjectStatus.PLANNED -> Icons.Outlined.EditCalendar
        ProjectStatus.IN_PROGRESS -> Icons.Outlined.PlayCircle
        ProjectStatus.PAUSED -> Icons.Outlined.PauseCircle
        ProjectStatus.DONE -> Icons.Outlined.CheckCircle
    }

@get:StringRes
val ProjectType.label: Int
    get() = when (this) {
        ProjectType.SEWING -> R.string.type_sewing
        ProjectType.UPCYCLING -> R.string.type_upcycling
        ProjectType.REPAIR -> R.string.type_repair
        ProjectType.ALTERATION -> R.string.type_alteration
    }

val ProjectType.icon: ImageVector
    get() = when (this) {
        ProjectType.SEWING -> Icons.Outlined.Checkroom
        ProjectType.UPCYCLING -> Icons.Outlined.Recycling
        ProjectType.REPAIR -> Icons.Outlined.Build
        ProjectType.ALTERATION -> Icons.Outlined.Straighten
    }

@get:StringRes
val PhotoTag.label: Int
    get() = when (this) {
        PhotoTag.INSPIRATION -> R.string.photo_tag_inspiration
        PhotoTag.PROGRESS -> R.string.photo_tag_progress
        PhotoTag.FINISHED -> R.string.photo_tag_finished
    }
