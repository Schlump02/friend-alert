package xyz.mycompany.friendalert.models

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import xyz.mycompany.friendalert.R

data class FrequencyUnit(val unit: String, val days: Int)

class FrequencyPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private val frequencyUnits = listOf(
        FrequencyUnit(context.getString(R.string.days), 1),
        FrequencyUnit(context.getString(R.string.weeks), 7),
        FrequencyUnit(context.getString(R.string.months), 30),
        FrequencyUnit(context.getString(R.string.years), 365)
    )

    init {
        layoutResource = R.layout.frequency_selection_layout
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        with(holder.itemView) {
            val frequencyValue = findViewById<EditText>(R.id.frequency_value)
            val frequencyUnit = findViewById<Spinner>(R.id.frequency_unit)

            findViewById<TextView>(R.id.frequency_title).text = title
            findViewById<TextView>(R.id.summary).text = summary

            initializeSpinner(frequencyUnit)
            loadCurrentPreferences(frequencyValue, frequencyUnit)

            frequencyValue.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    savePreference(
                        frequencyValue.text.toString(),
                        frequencyUnit.selectedItem.toString()
                    )
                }
            }

            frequencyUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    savePreference(
                        frequencyValue.text.toString(),
                        frequencyUnit.selectedItem.toString()
                    )
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    }

    private fun initializeSpinner(spinner: Spinner) {
        spinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            frequencyUnits.map { it.unit })
    }

    private fun loadCurrentPreferences(valueEditText: EditText, unitSpinner: Spinner) {
        val currentSettingDays = getPersistedInt(1)
        val (matchingUnit, valueInUnit) = findMatchingUnitAndValue(currentSettingDays)

        valueEditText.setText(valueInUnit.toString())
        unitSpinner.setSelection(frequencyUnits.indexOf(matchingUnit))
    }

    private fun findMatchingUnitAndValue(currentDays: Int): Pair<FrequencyUnit, Int> {
        return frequencyUnits
            .firstOrNull { currentDays % it.days == 0 }
            ?.let { it to currentDays / it.days }
            ?: (frequencyUnits.first() to currentDays)
    }

    private fun savePreference(value: String, unit: String) {
        val totalDays = (frequencyUnits.find { it.unit == unit }?.days ?: 1) * value.toInt()
        persistInt(totalDays)
    }
}