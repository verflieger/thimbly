package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.ui.common.Avatar
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.common.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Short create form (SPEC 3.3): name, type, person, patterns. Everything else is edited on the detail screen.
 * [onCreated] receives the new project's ID.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProjectDialog(
    persons: List<PersonEntity>,
    patterns: List<PatternEntity>,
    createPerson: suspend (String) -> String,
    createPattern: suspend (String) -> String,
    createProject: suspend (String, ProjectType, String?, List<String>) -> String,
    onCreated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(ProjectType.SEWING) }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    val patternIds = remember { mutableStateListOf<String>() }
    var picking by remember { mutableStateOf<Picker?>(null) }
    // Names of items created in this dialog, until the lists catch up with the database.
    val createdNames = remember { mutableStateOf(mapOf<String, String>()) }
    fun patternName(id: String) = patterns.firstOrNull { it.id == id }?.name ?: createdNames.value[id].orEmpty()
    val person = personId?.let { id -> persons.firstOrNull { it.id == id } }
    val personName = person?.name ?: personId?.let { createdNames.value[it] }

    // One dialog at a time: while a picker is open it replaces this dialog (state above is kept).
    if (picking == null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("newProjectName"),
                )
                Text(stringResource(R.string.project_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectType.entries.forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(stringResource(option.label)) })
                    }
                }
                Text(stringResource(R.string.project_person), style = MaterialTheme.typography.labelLarge)
                AssistChip(
                    onClick = { picking = Picker.PERSON },
                    label = { Text(personName ?: stringResource(R.string.project_no_person)) },
                    leadingIcon = {
                        if (personName != null) Avatar(personName, person?.avatarColor ?: 0xFF7F766B.toInt(), size = 18.dp)
                        else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                    },
                )
                Text(stringResource(R.string.project_patterns), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    patternIds.forEach { id ->
                        InputChip(
                            selected = false,
                            onClick = { patternIds.remove(id) },
                            label = { Text(patternName(id)) },
                            trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove), Modifier.size(18.dp)) },
                        )
                    }
                    AssistChip(
                        onClick = { picking = Picker.PATTERN },
                        label = { Text(stringResource(R.string.project_add_pattern)) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val id = createProject(name, type, personId, patternIds.toList())
                        // Navigation must run on the main thread, wherever the database call resumed.
                        withContext(Dispatchers.Main) { onCreated(id) }
                    }
                },
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    when (picking) {
        Picker.PERSON -> EntityPicker(
            title = stringResource(R.string.picker_choose_person),
            items = persons.map { PickerItem(it.id, it.name, avatarColor = it.avatarColor) },
            noneLabel = stringResource(R.string.project_no_person),
            onPick = { personId = it; picking = null },
            onCreate = { newName ->
                scope.launch {
                    val id = createPerson(newName)
                    createdNames.value += id to newName
                    personId = id
                    picking = null
                }
            },
            onDismiss = { picking = null },
        )
        Picker.PATTERN -> EntityPicker(
            title = stringResource(R.string.picker_choose_pattern),
            items = patterns.filter { it.id !in patternIds }.map { PickerItem(it.id, it.name, it.designer) },
            onPick = { id -> if (id != null) patternIds.add(id); picking = null },
            onCreate = { newName ->
                scope.launch {
                    val id = createPattern(newName)
                    createdNames.value += id to newName
                    patternIds.add(id)
                    picking = null
                }
            },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

internal enum class Picker { PERSON, PATTERN }
