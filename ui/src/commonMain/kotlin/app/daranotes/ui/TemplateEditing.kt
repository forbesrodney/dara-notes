package app.daranotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.daranotes.core.FoamVariables
import app.daranotes.core.TemplateSample

/** Above a template's preview: that it's an example, made how, and where the note would go. */
@Composable
fun TemplateSampleBar(sample: TemplateSample) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                if (sample.daily) "Example: today's daily note, made from this template"
                else "Example: a note titled \"${sample.title}\", made now from this template",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "Saved as ${sample.notePath}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Every variable worth inserting, grouped, with what each becomes; tap one to insert it. */
@Composable
fun VariablePickerDialog(onDismiss: () -> Unit, onHelp: () -> Unit, onPick: (String) -> Unit) {
    val groups = FoamVariables.catalog.groupBy { it.group }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert variable") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                for ((group, vars) in groups) {
                    item(key = group) {
                        Text(
                            group,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(vars, key = { it.name }) { v ->
                        ListItem(
                            headlineContent = { Text("$" + v.name, fontFamily = FontFamily.Monospace) },
                            supportingContent = { Text(v.description) },
                            // The dialog's own color, not a lighter band per row.
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPick(v.name) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onHelp) { Text("Help") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Material's "data_object" icon, curly braces: for inserting a variable. */
val VariableIcon: ImageVector by lazy {
    menuIcon(
        "Variable",
        "M4,7v2c0,0.55 -0.45,1 -1,1H2v4h1c0.55,0 1,0.45 1,1v2c0,1.65 1.35,3 3,3h3v-2H7c-0.55,0 -1,-0.45 -1,-1v-2c0,-1.3 -0.84,-2.42 -2,-2.83v-0.34C5.16,11.42 6,10.3 6,9V7c0,-0.55 0.45,-1 1,-1h3V4H7C5.35,4 4,5.35 4,7z" +
            "M21,10c-0.55,0 -1,-0.45 -1,-1V7c0,-1.65 -1.35,-3 -3,-3h-3v2h3c0.55,0 1,0.45 1,1v2c0,1.3 0.84,2.42 2,2.83v0.34c-1.16,0.41 -2,1.52 -2,2.83v2c0,0.55 -0.45,1 -1,1h-3v2h3c1.65,0 3,-1.35 3,-3v-2c0,-0.55 0.45,-1 1,-1h1v-4H21z",
    )
}
