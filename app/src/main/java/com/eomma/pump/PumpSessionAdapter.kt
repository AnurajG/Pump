package com.eomma.pump

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PumpSession(
    val id: Long = 0,
    val timestamp: Date,
    val totalAmount: Double,
    val leftAmount: Double,
    val rightAmount: Double,
    val durationMinutes: Int
)

class PumpSessionAdapter(
    private val onSessionUpdate: ((PumpSession, Double, Double) -> Unit)? = null
) : ListAdapter<PumpSession, PumpSessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pump_session, parent, false)
        return SessionViewHolder(view, onSessionUpdate)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionViewHolder(
        itemView: View,
        private val onSessionUpdate: ((PumpSession, Double, Double) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val timeValue: TextView = itemView.findViewById(R.id.timeValue)
        private val timePeriod: TextView = itemView.findViewById(R.id.timePeriod)
        private val durationValue: TextView = itemView.findViewById(R.id.durationValue)
        private val totalAmountValue: TextView = itemView.findViewById(R.id.totalAmountValue)
        private val leftAmountValue: TextView = itemView.findViewById(R.id.leftAmountValue)
        private val rightAmountValue: TextView = itemView.findViewById(R.id.rightAmountValue)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)

        private val timeFormatter = SimpleDateFormat("h:mm", Locale.getDefault())
        private val periodFormatter = SimpleDateFormat("a", Locale.getDefault())

        fun bind(session: PumpSession) {
            // Set time (split into value and period)
            timeValue.text = timeFormatter.format(session.timestamp)
            timePeriod.text = periodFormatter.format(session.timestamp)

            // Set duration
            durationValue.text = session.durationMinutes.toString()

            // Set amounts
            totalAmountValue.text = String.format("%.1f", session.totalAmount)
            leftAmountValue.text = String.format("%.1f", session.leftAmount)
            rightAmountValue.text = String.format("%.1f", session.rightAmount)

            // Set edit button click listener
            editButton.setOnClickListener {
                showEditDialog(itemView.context, session)
            }
        }

        private fun showEditDialog(context: Context, session: PumpSession) {
            val dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_edit_session, null)

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create()

            val leftInput = dialogView.findViewById<EditText>(R.id.leftInput)
            val rightInput = dialogView.findViewById<EditText>(R.id.rightInput)
            val saveButton = dialogView.findViewById<MaterialButton>(R.id.saveButton)
            val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelButton)

            // Set current values for both measurements
            leftInput.setText(if (session.leftAmount > 0) String.format("%.1f", session.leftAmount) else "")
            rightInput.setText(if (session.rightAmount > 0) String.format("%.1f", session.rightAmount) else "")

            // Add focus change listeners to select all text
            leftInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    leftInput.selectAll()
                }
            }

            rightInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    rightInput.selectAll()
                }
            }

            saveButton.setOnClickListener {
                val leftAmount = leftInput.text.toString().toDoubleOrNull() ?: 0.0
                val rightAmount = rightInput.text.toString().toDoubleOrNull() ?: 0.0

                if (leftAmount == 0.0 && rightAmount == 0.0) {
                    Toast.makeText(
                        context,
                        "Please enter at least one measurement",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onSessionUpdate?.invoke(session, leftAmount, rightAmount)
                    dialog.dismiss()
                }
            }

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    private class SessionDiffCallback : DiffUtil.ItemCallback<PumpSession>() {
        override fun areItemsTheSame(oldItem: PumpSession, newItem: PumpSession): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: PumpSession, newItem: PumpSession): Boolean {
            return oldItem == newItem
        }
    }
}