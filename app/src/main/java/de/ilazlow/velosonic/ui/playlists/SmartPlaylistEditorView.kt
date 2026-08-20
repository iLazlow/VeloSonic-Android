package de.ilazlow.velosonic.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.playlist.SPCriteria
import de.ilazlow.velosonic.data.playlist.SPFieldDef
import de.ilazlow.velosonic.data.playlist.SPFieldType
import de.ilazlow.velosonic.data.playlist.SPRule
import de.ilazlow.velosonic.data.playlist.spRuleDateFormat

/**
 * Direct Compose port of SmartPlaylistEditorView.swift: match all/any picker, the rule list (each
 * a field-menu + operator-menu + type-appropriate value input + delete button), a sort-by/order
 * picker (including iOS's "+random" option), and an optional result-count limit. Embedded directly
 * into [CreateEditPlaylistSheet]'s Column — this project's smart playlists are small enough in
 * practice that a non-lazy Column (matching the sheet's own non-lazy layout) is fine.
 */
@Composable
fun SmartPlaylistEditorSection(
    criteria: SPCriteria,
    onCriteriaChange: (SPCriteria) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        SmartSectionHeader(stringResource(R.string.smart_playlist_editor_section_match))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            val matchAllLabel = stringResource(R.string.smart_playlist_editor_match_all)
            val matchAnyLabel = stringResource(R.string.smart_playlist_editor_match_any)
            Text(stringResource(R.string.smart_playlist_editor_match_label), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(8.dp))
            SmartChipMenu(
                label = if (criteria.matchAll) matchAllLabel else matchAnyLabel,
                options = listOf("all" to matchAllLabel, "any" to matchAnyLabel),
                onSelect = { key -> onCriteriaChange(criteria.copy(matchAll = key == "all")) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.smart_playlist_editor_of_following_rules), style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))
        SmartSectionHeader(stringResource(R.string.smart_playlist_editor_section_rules))
        criteria.rules.forEach { rule ->
            SmartPlaylistRuleRow(
                rule = rule,
                onChange = { updated ->
                    onCriteriaChange(criteria.copy(rules = criteria.rules.map { if (it.id == rule.id) updated else it }))
                },
                onDelete = {
                    val remaining = criteria.rules.filterNot { it.id == rule.id }
                    onCriteriaChange(criteria.copy(rules = remaining.ifEmpty { listOf(SPRule()) }))
                }
            )
            HorizontalDivider()
        }
        TextButton(onClick = { onCriteriaChange(criteria.copy(rules = criteria.rules + SPRule())) }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.smart_playlist_editor_add_rule))
        }

        Spacer(modifier = Modifier.height(12.dp))
        SmartSectionHeader(stringResource(R.string.smart_playlist_editor_section_sort))
        val randomLabel = stringResource(R.string.smart_playlist_editor_sort_random)
        val sortOptions = remember(randomLabel) { listOf("+random" to randomLabel) + SPFieldDef.all.map { it.key to it.label } }
        val currentSortLabel = sortOptions.firstOrNull { it.first == criteria.sortField }?.second ?: criteria.sortField
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.smart_playlist_editor_sort_by_label), style = MaterialTheme.typography.bodyLarge)
            SmartChipMenu(label = currentSortLabel, options = sortOptions, onSelect = { key -> onCriteriaChange(criteria.copy(sortField = key)) })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ascendingLabel = stringResource(R.string.smart_playlist_editor_ascending)
            val descendingLabel = stringResource(R.string.smart_playlist_editor_descending)
            Text(stringResource(R.string.smart_playlist_editor_order_label), style = MaterialTheme.typography.bodyLarge)
            SmartChipMenu(
                label = if (criteria.sortOrder == "desc") descendingLabel else ascendingLabel,
                options = listOf("asc" to ascendingLabel, "desc" to descendingLabel),
                onSelect = { key -> onCriteriaChange(criteria.copy(sortOrder = key)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.smart_playlist_editor_limit_results_label), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = criteria.limitEnabled, onCheckedChange = { onCriteriaChange(criteria.copy(limitEnabled = it)) })
        }
        if (criteria.limitEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.smart_playlist_editor_limit_songs_count, criteria.limit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = { onCriteriaChange(criteria.copy(limit = (criteria.limit - 10).coerceAtLeast(1))) }) { Text("−") }
                    TextButton(onClick = { onCriteriaChange(criteria.copy(limit = (criteria.limit + 10).coerceAtMost(10000))) }) { Text("+") }
                }
            }
        }
    }
}

@Composable
private fun SmartSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SmartChipMenu(label: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, optLabel) ->
                DropdownMenuItem(text = { Text(optLabel) }, onClick = { expanded = false; onSelect(key) })
            }
        }
    }
}

@Composable
private fun SmartPlaylistRuleRow(rule: SPRule, onChange: (SPRule) -> Unit, onDelete: () -> Unit) {
    val fieldDef = SPFieldDef.def(rule.field)
    val currentOpLabel = fieldDef.availableOps.firstOrNull { it.key == rule.op }?.label ?: rule.op

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmartChipMenu(
                label = fieldDef.label,
                options = SPFieldDef.all.map { it.key to it.label },
                onSelect = { key ->
                    if (key == rule.field) return@SmartChipMenu
                    val newDef = SPFieldDef.def(key)
                    val newOp = if (newDef.availableOps.none { it.key == rule.op }) newDef.defaultOp else rule.op
                    onChange(rule.copy(field = key, op = newOp, stringValue = "", boolValue = true))
                }
            )
            SmartChipMenu(
                label = currentOpLabel,
                options = fieldDef.availableOps.map { it.key to it.label },
                onSelect = { op -> onChange(rule.copy(op = op)) }
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.smart_playlist_editor_remove_rule_content_description), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SmartRuleValueInput(fieldDef, rule, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartRuleValueInput(fieldDef: SPFieldDef, rule: SPRule, onChange: (SPRule) -> Unit) {
    when (fieldDef.type) {
        SPFieldType.BOOLEAN -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rule.boolValue, onCheckedChange = { onChange(rule.copy(boolValue = it)) })
                Text(
                    if (rule.boolValue) stringResource(R.string.smart_playlist_editor_true) else stringResource(R.string.smart_playlist_editor_false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SPFieldType.TEXT -> {
            OutlinedTextField(
                value = rule.stringValue,
                onValueChange = { onChange(rule.copy(stringValue = it)) },
                placeholder = { Text(stringResource(R.string.smart_playlist_editor_value_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SPFieldType.NUMBER, SPFieldType.RATING -> {
            if (rule.op == "inTheRange") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = rule.rangeMin.toString(),
                        onValueChange = { onChange(rule.copy(rangeMin = it.toIntOrNull() ?: rule.rangeMin)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = rule.rangeMax.toString(),
                        onValueChange = { onChange(rule.copy(rangeMax = it.toIntOrNull() ?: rule.rangeMax)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                OutlinedTextField(
                    value = rule.stringValue,
                    onValueChange = { onChange(rule.copy(stringValue = it)) },
                    placeholder = { Text(stringResource(R.string.smart_playlist_editor_number_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SPFieldType.DATE -> {
            if (rule.op == "inTheLast" || rule.op == "notInTheLast") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rule.stringValue,
                        onValueChange = { onChange(rule.copy(stringValue = it)) },
                        placeholder = { Text("30") },
                        singleLine = true,
                        modifier = Modifier.size(width = 90.dp, height = 56.dp)
                    )
                    Text(stringResource(R.string.smart_playlist_editor_days_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                var showPicker by remember { mutableStateOf(false) }
                TextButton(onClick = { showPicker = true }) {
                    Text(spRuleDateFormat.format(rule.dateValue))
                }
                if (showPicker) {
                    val state = rememberDatePickerState(initialSelectedDateMillis = rule.dateValue.time)
                    DatePickerDialog(
                        onDismissRequest = { showPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                state.selectedDateMillis?.let { onChange(rule.copy(dateValue = java.util.Date(it))) }
                                showPicker = false
                            }) { Text(stringResource(R.string.smart_playlist_editor_ok)) }
                        },
                        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.smart_playlist_editor_cancel)) } }
                    ) {
                        DatePicker(state = state)
                    }
                }
            }
        }
    }
}
