package edu.uw.mqazi.geopaint

import android.support.v7.app.AppCompatActivity
import android.os.Bundle


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }
}