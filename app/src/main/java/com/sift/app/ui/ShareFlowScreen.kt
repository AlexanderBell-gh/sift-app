package com.sift.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sift.app.data.PinOutcome
import com.sift.app.data.TrialBlockReason
import com.sift.app.lib.ParsedShare
import com.sift.app.lib.STORES

/**
 * Phase 3 confirm screen: image, name, price/offer prefilled where known,
 * price prompted at pin time. Inherited facts (Phase 1 `resolve`) arrive as
 * [prefill] and stay editable — the user always confirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFlowScreen(
    parsed: ParsedShare,
    prefillPrice: String,
    pinning: Boolean,
    outcome: PinOutcome?,
    onPin: (storeName: String, name: String, price: Double?) -> Unit,
    onDone: () -> Unit,
) {
    var name by remember(parsed) { mutableStateOf(parsed.title) }
    var price by remember(prefillPrice) { mutableStateOf(prefillPrice) }
    var storeExpanded by remember { mutableStateOf(false) }
    var selectedStoreId by remember(parsed) { mutableStateOf(parsed.storeId ?: "tesco") }
    val selectedStore = STORES.first { it.id == selectedStoreId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pin to watchlist", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = storeExpanded,
            onExpandedChange = { storeExpanded = !storeExpanded },
        ) {
            OutlinedTextField(
                value = selectedStore.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Store") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(storeExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = storeExpanded,
                onDismissRequest = { storeExpanded = false },
            ) {
                STORES.forEach { store ->
                    DropdownMenuItem(
                        text = { Text(store.name) },
                        onClick = {
                            selectedStoreId = store.id
                            storeExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text("Shelf price (£)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        when (outcome) {
            is PinOutcome.Pinned -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (outcome.alreadyPinned) "Already pinned — you're tracking this."
                    else "Pinned to your watchlist.",
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
            is PinOutcome.TrialBlocked -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    when (outcome.reason) {
                        TrialBlockReason.TRIAL_EXPIRED -> "Your trial has expired — upgrade to keep pinning."
                        TrialBlockReason.WATCHLIST_LIMIT -> "Trial limit reached (5 items) — upgrade to pin more."
                        TrialBlockReason.UNKNOWN -> "Trial limit reached — upgrade to keep pinning."
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is PinOutcome.Failed -> {
                Spacer(Modifier.height(12.dp))
                Text(outcome.message, color = MaterialTheme.colorScheme.error)
            }
            null -> {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val parsedPrice = price.trim().toDoubleOrNull()
                            onPin(selectedStore.name, name.trim(), parsedPrice)
                        },
                        enabled = !pinning && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (pinning) CircularProgressIndicator() else Text("Pin")
                    }
                }
            }
        }
    }
}
