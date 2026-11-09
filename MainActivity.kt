package com.sep.palmeiras

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppItem(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dateView: TextView
    private lateinit var adapter: AppAdapter
    private var all: List<AppItem> = emptyList()
    private var shown: List<AppItem> = emptyList()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("sep", MODE_PRIVATE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(44), dp(16), dp(16))

        val clock = TextClock(this)
        clock.setFormat24Hour("HH:mm")
        clock.setFormat12Hour("HH:mm")
        clock.textSize = 52f
        clock.setTextColor(Color.WHITE)
        clock.gravity = Gravity.CENTER
        clock.setShadowLayer(6f, 0f, 1f, Color.BLACK)
        clock.setOnClickListener {
            if (prefs.contains("pin")) {
                askPin("PIN") { showHidden() }
            } else {
                Toast.makeText(this, "Segure um app para esconder", Toast.LENGTH_SHORT).show()
            }
        }

        dateView = TextView(this)
        dateView.setTextColor(Color.WHITE)
        dateView.gravity = Gravity.CENTER
        dateView.textSize = 15f
        dateView.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        dateView.setPadding(0, 0, 0, dp(16))

        val grid = GridView(this)
        grid.numColumns = 4
        grid.verticalSpacing = dp(14)
        grid.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        adapter = AppAdapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> launch(shown[pos].pkg) }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            options(shown[pos])
            true
        }

        root.addView(clock)
        root.addView(dateView)
        root.addView(grid)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        dateView.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date())
        loadApps()
        refresh()
    }

    @Deprecated("Launcher: ignora o botao voltar")
    override fun onBackPressed() {
        // launcher nao fecha com voltar
    }

    private fun loadApps() {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .filter { it.pkg != packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun hiddenSet(): MutableSet<String> =
        HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())

    private fun saveHidden(s: Set<String>) {
        prefs.edit().putStringSet("hidden", s).apply()
    }

    private fun refresh() {
        val hs = hiddenSet()
        shown = all.filter { it.pkg !in hs }
        adapter.notifyDataSetChanged()
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent)
    }

    private fun options(app: AppItem) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Esconder", "Informações do app")) { _, which ->
                if (which == 0) {
                    ensurePin {
                        val s = hiddenSet()
                        s.add(app.pkg)
                        saveHidden(s)
                        refresh()
                    }
                } else {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                    )
                }
            }
            .show()
    }

    private fun showHidden() {
        val hs = hiddenSet()
        val list = all.filter { it.pkg in hs }
        if (list.isEmpty()) {
            Toast.makeText(this, "Nenhum app escondido", Toast.LENGTH_SHORT).show()
            return
        }
        val lv = ListView(this)
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list.map { it.label })
        val dlg = AlertDialog.Builder(this)
            .setTitle("Escondidos")
            .setView(lv)
            .setNegativeButton("Fechar", null)
            .create()
        lv.setOnItemClickListener { _, _, pos, _ ->
            dlg.dismiss()
            launch(list[pos].pkg)
        }
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setTitle("Mostrar " + list[pos].label + " de novo?")
                .setPositiveButton("Mostrar") { _, _ ->
                    val s = hiddenSet()
                    s.remove(list[pos].pkg)
                    saveHidden(s)
                    refresh()
                    dlg.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
        dlg.show()
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("sep:" + s).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun pinField(hint: String): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        e.hint = hint
        return e
    }

    private fun askPin(title: String, onOk: () -> Unit) {
        val e = pinField("PIN")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(e)
            .setPositiveButton("OK") { _, _ ->
                if (hash(e.text.toString()) == prefs.getString("pin", "")) {
                    onOk()
                } else {
                    Toast.makeText(this, "PIN errado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ensurePin(then: () -> Unit) {
        if (prefs.contains("pin")) {
            then()
            return
        }
        val e = pinField("PIN (4 a 8 números)")
        AlertDialog.Builder(this)
            .setTitle("Crie um PIN")
            .setView(e)
            .setPositiveButton("Salvar") { _, _ ->
                val v = e.text.toString()
                if (v.length in 4..8) {
                    prefs.edit().putString("pin", hash(v)).apply()
                    then()
                } else {
                    Toast.makeText(this, "Use de 4 a 8 números", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = shown[position]
            val box = LinearLayout(this@MainActivity)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            val img = ImageView(this@MainActivity)
            img.setImageDrawable(a.icon)
            img.layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            val t = TextView(this@MainActivity)
            t.text = a.label
            t.setTextColor(Color.WHITE)
            t.textSize = 11f
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER
            t.setShadowLayer(3f, 0f, 1f, Color.BLACK)
            box.addView(img)
            box.addView(t)
            return box
        }
    }
}
