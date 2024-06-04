package com.mnn.llm;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class BaseActivity extends AppCompatActivity {
    Toolbar toolbar;
    TextView title;

    public final void changeTitle(int toolbarId, String titlePage){
        toolbar = findViewById(toolbarId);
        setSupportActionBar(toolbar);

        title = toolbar.findViewById(R.id.tv_title);
        if (title != null) {
            title.setText(titlePage);
        }
        getSupportActionBar().setTitle("");
    }

    public final void setupToolbar(int toolbarId, String titlePage){
        toolbar = findViewById(toolbarId);
        setSupportActionBar(toolbar);

        title = toolbar.findViewById(R.id.tv_title);
        if (title != null) {
            title.setText(titlePage);
        }
        getSupportActionBar().setTitle("");
    }

    public void setupToolbarWithUpNav(int toolbarId, String titlePage, @DrawableRes int res){
        toolbar = findViewById(toolbarId);
        setSupportActionBar(toolbar);

        title = toolbar.findViewById(R.id.tv_title);
        if (title != null) {
            title.setText(titlePage);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(res);
        getSupportActionBar().setTitle("");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }
}
