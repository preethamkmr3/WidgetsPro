package com.tpk.widgetspro.widgets.sports

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.sports.SportsWidgetService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class Team(val strTeam: String, val strBadge: String?) {
    override fun toString(): String = strTeam
}

class SportsWidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var sportSpinner: Spinner
    private lateinit var leagueSpinner: Spinner
    private lateinit var homeTeamSpinner: Spinner
    private lateinit var awayTeamSpinner: Spinner
    private lateinit var saveButton: Button

    private var sports = listOf<String>()
    private var leagues = listOf<String>()
    private var teams = listOf<Team>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupView()
        fetchSports()
    }

    private fun setupView() {
        window.apply {
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.8).toInt()
            setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes.dimAmount = 0.5f
            setBackgroundDrawableResource(R.color.transparent)
        }
        setFinishOnTouchOutside(true)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.rounded_layout_bg_alt)
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val titleTextView = TextView(this).apply {
            text = getString(R.string.select_teams)
            setTextColor(ContextCompat.getColor(this@SportsWidgetConfigActivity, R.color.text_color))
            textSize = 18f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, dpToPx(8))
        }
        layout.addView(titleTextView)

        sportSpinner = Spinner(this)
        leagueSpinner = Spinner(this)
        homeTeamSpinner = Spinner(this)
        awayTeamSpinner = Spinner(this)
        saveButton = Button(this).apply { text = getString(R.string.save) }

        val spinnerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(8)
        }

        layout.addView(sportSpinner, spinnerParams)
        layout.addView(leagueSpinner, spinnerParams)
        layout.addView(homeTeamSpinner, spinnerParams)
        layout.addView(awayTeamSpinner, spinnerParams)
        layout.addView(saveButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(16)
        })

        setContentView(layout)

        sportSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (sports.isNotEmpty()) {
                    fetchLeagues(sports[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        leagueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (leagues.isNotEmpty()) {
                    fetchTeams(leagues[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        saveButton.setOnClickListener {
            if (homeTeamSpinner.selectedItem != null && awayTeamSpinner.selectedItem != null) {
                val homeTeam = homeTeamSpinner.selectedItem.toString()
                val awayTeam = awayTeamSpinner.selectedItem.toString()

                val prefs = getSharedPreferences("SportsWidgetPrefs", MODE_PRIVATE).edit()
                prefs.putString("home_team", homeTeam)
                prefs.putString("away_team", awayTeam)
                prefs.apply()

                sendBroadcast(Intent("REFRESH_WIDGET"))

                val serviceIntent = Intent(this, SportsWidgetService::class.java)
                startService(serviceIntent)

                val appWidgetManager = AppWidgetManager.getInstance(this)
                updateAppWidget(this, appWidgetManager, appWidgetId)

                val resultValue = Intent()
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            } else {
                Toast.makeText(this, R.string.toast_please_select_teams, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchSports() {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    URL("https://www.thesportsdb.com/api/v1/json/123/all_sports.php").readText()
                }
                val json = JSONObject(result)
                val sportsArray = json.getJSONArray("sports")
                val sportList = mutableListOf<String>()
                for (i in 0 until sportsArray.length()) {
                    sportList.add(sportsArray.getJSONObject(i).getString("strSport"))
                }
                sports = sportList
                val adapter = ArrayAdapter(this@SportsWidgetConfigActivity, R.layout.custom_simple_list_item, sports)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                sportSpinner.adapter = adapter
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SportsWidgetConfigActivity, R.string.toast_failed_load_sports, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchLeagues(sport: String) {
        scope.launch {
            try {
                val encodedSport = sport.replace(" ", "%20")
                val result = withContext(Dispatchers.IO) {
                    URL("https://www.thesportsdb.com/api/v1/json/123/search_all_leagues.php?s=$encodedSport").readText()
                }
                val json = JSONObject(result)
                val leaguesArray = json.getJSONArray("countries")
                val leagueList = mutableListOf<String>()
                for (i in 0 until leaguesArray.length()) {
                    leagueList.add(leaguesArray.getJSONObject(i).getString("strLeague"))
                }
                leagues = leagueList
                val adapter = ArrayAdapter(this@SportsWidgetConfigActivity, R.layout.custom_simple_list_item, leagues)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                leagueSpinner.adapter = adapter
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SportsWidgetConfigActivity, R.string.toast_failed_load_leagues, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchTeams(league: String) {
        scope.launch {
            try {
                val encodedLeague = league.replace(" ", "%20")
                val result = withContext(Dispatchers.IO) {
                    URL("https://www.thesportsdb.com/api/v1/json/123/search_all_teams.php?l=$encodedLeague").readText()
                }
                val json = JSONObject(result)
                val teamsArray = json.getJSONArray("teams")
                val teamList = mutableListOf<Team>()
                for (i in 0 until teamsArray.length()) {
                    val teamJson = teamsArray.getJSONObject(i)
                    teamList.add(Team(teamJson.getString("strTeam"), teamJson.optString("strBadge", null)))
                }
                teams = teamList
                val adapter = TeamAdapter(this@SportsWidgetConfigActivity, teams)
                homeTeamSpinner.adapter = adapter
                awayTeamSpinner.adapter = adapter
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SportsWidgetConfigActivity, R.string.toast_failed_load_teams, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

class TeamAdapter(
    context: Context,
    private val teams: List<Team>
) : ArrayAdapter<Team>(context, 0, teams) {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                    marginEnd = dpToPx(8)
                }
            }
            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 16f
            }
            addView(imageView)
            addView(textView)
        }

        val team = getItem(position)!!
        val imageView = view.getChildAt(0) as ImageView
        val textView = view.getChildAt(1) as TextView

        textView.text = team.strTeam

        if (!team.strBadge.isNullOrEmpty()) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val url = URL("${team.strBadge}/tiny")
                        val input = url.openStream()
                        BitmapFactory.decodeStream(input)
                    }
                    imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            imageView.setImageDrawable(null)
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}