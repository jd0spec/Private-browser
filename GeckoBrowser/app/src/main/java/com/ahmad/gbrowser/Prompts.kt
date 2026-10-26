package com.ahmad.gbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * Handles website pop-ups: alert/confirm/prompt boxes, dropdown menus (<select>)
 * and the file picker for uploads.
 */
class Prompts(private val act: MainActivity) : PromptDelegate {

    private var filePrompt: PromptDelegate.FilePrompt? = null
    private var fileResult: GeckoResult<PromptResponse>? = null

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.AlertPrompt
    ): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        act.runOnUiThread {
            AlertDialog.Builder(act)
                .setTitle(prompt.title ?: "")
                .setMessage(prompt.message ?: "")
                .setPositiveButton("OK") { _, _ -> res.complete(prompt.dismiss()) }
                .setOnCancelListener { res.complete(prompt.dismiss()) }
                .show()
        }
        return res
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.ButtonPrompt
    ): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        act.runOnUiThread {
            AlertDialog.Builder(act)
                .setTitle(prompt.title ?: "")
                .setMessage(prompt.message ?: "")
                .setPositiveButton("OK") { _, _ ->
                    res.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE))
                }
                .setNegativeButton("Cancel") { _, _ ->
                    res.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                }
                .setOnCancelListener { res.complete(prompt.dismiss()) }
                .show()
        }
        return res
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.TextPrompt
    ): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        act.runOnUiThread {
            val input = EditText(act)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(prompt.defaultValue ?: "")
            AlertDialog.Builder(act)
                .setTitle(prompt.title ?: "")
                .setMessage(prompt.message ?: "")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    res.complete(prompt.confirm(input.text.toString()))
                }
                .setNegativeButton("Cancel") { _, _ -> res.complete(prompt.dismiss()) }
                .setOnCancelListener { res.complete(prompt.dismiss()) }
                .show()
        }
        return res
    }

    private fun flatten(
        src: Array<PromptDelegate.ChoicePrompt.Choice>?,
        out: MutableList<PromptDelegate.ChoicePrompt.Choice>
    ) {
        if (src == null) return
        for (c in src) {
            val kids = c.items
            if (kids != null && kids.isNotEmpty()) flatten(kids, out)
            else if (!c.separator) out.add(c)
        }
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: PromptDelegate.ChoicePrompt
    ): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        act.runOnUiThread {
            val flat = ArrayList<PromptDelegate.ChoicePrompt.Choice>()
            flatten(prompt.choices, flat)
            val labels = Array(flat.size) { flat[it].label ?: "" }
            val b = AlertDialog.Builder(act)
            if (!prompt.title.isNullOrEmpty()) b.setTitle(prompt.title)
            if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                val checked = BooleanArray(flat.size) { flat[it].selected }
                b.setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
                b.setPositiveButton("OK") { _, _ ->
                    val ids = ArrayList<String>()
                    for (i in flat.indices) if (checked[i]) ids.add(flat[i].id)
                    res.complete(prompt.confirm(ids.toTypedArray()))
                }
                b.setNegativeButton("Cancel") { _, _ -> res.complete(prompt.dismiss()) }
            } else {
                b.setItems(labels) { _, i -> res.complete(prompt.confirm(flat[i].id)) }
            }
            b.setOnCancelListener { res.complete(prompt.dismiss()) }
            b.show()
        }
        return res
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: PromptDelegate.FilePrompt
    ): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        filePrompt = prompt
        fileResult = res
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        val types = (prompt.mimeTypes ?: emptyArray()).filter { it.contains("/") }
        if (types.size == 1) {
            i.type = types[0]
        } else {
            i.type = "*/*"
            if (types.size > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        }
        if (prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE) {
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        act.runOnUiThread {
            try {
                act.startActivityForResult(i, MainActivity.REQ_FILE)
            } catch (e: Exception) {
                filePrompt = null
                fileResult = null
                res.complete(prompt.dismiss())
            }
        }
        return res
    }

    fun onFileResult(resultCode: Int, data: Intent?) {
        val p = filePrompt ?: return
        val r = fileResult ?: return
        filePrompt = null
        fileResult = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            r.complete(p.dismiss())
            return
        }
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            val uris = Array<Uri>(clip.itemCount) { clip.getItemAt(it).uri }
            if (p.type == PromptDelegate.FilePrompt.Type.MULTIPLE) r.complete(p.confirm(act as android.content.Context, uris))
            else r.complete(p.confirm(act as android.content.Context, uris[0]))
        } else {
            val u = data.data
            if (u != null) r.complete(p.confirm(act as android.content.Context, u)) else r.complete(p.dismiss())
        }
    }
}
