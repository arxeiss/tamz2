package com.example.adamfousek.tickitoprojekt.activities;

import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;


import com.example.adamfousek.tickitoprojekt.AESCrypt;
import com.example.adamfousek.tickitoprojekt.models.ApiClient;
import com.example.adamfousek.tickitoprojekt.models.Codes;
import com.example.adamfousek.tickitoprojekt.models.Event;
import com.example.adamfousek.tickitoprojekt.EventAdapter;
import com.example.adamfousek.tickitoprojekt.R;
import com.example.adamfousek.tickitoprojekt.models.User;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ListOfEventsActivity extends AppCompatActivity {

    // Retrofit
    private final Retrofit.Builder builder = new Retrofit.Builder()
            .baseUrl("https://www.tickito.cz/")
            .addConverterFactory(GsonConverterFactory.create());
    private final Retrofit retrofit = builder.build();

    // SharedPreferences
    private SharedPreferences mySharedPref;
    private SharedPreferences.Editor mySharedEditor;

    // Informace o uživateli
    private User user = new User();
    private Codes codes = new Codes();
    private String login;
    private String password;
    private ListView lv;
    private EventAdapter adapter;

    private Timer timer;

    private boolean activeLoE = false;

    @Override
    public void onStart() {
        super.onStart();
        activeLoE = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        activeLoE = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        activeLoE = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_of_events);

        // Ziskání SharedPreferences
        mySharedPref = getSharedPreferences("myPref", Context.MODE_PRIVATE);

        // Získání informací o uživateli
        Intent intent = getIntent();
        user = (User)intent.getSerializableExtra("User");
        login = mySharedPref.getString("name", "");
        password = mySharedPref.getString("password", "");
        try {
            password = AESCrypt.decrypt(password);
        } catch (Exception e){
            e.printStackTrace();
        }
        activeLoE = true;
        setRepeatingAsyncTask();
        displayData();

    }

    // Přídání menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();

        if(id == R.id.logout){
            // Při odhlášení smažeme SharedPreferences aby se uživatel znovu nepřihlásil
            mySharedEditor = mySharedPref.edit();
            mySharedEditor.putString("name", "");
            mySharedEditor.putString("password", "");
            mySharedEditor.putLong("timestamp", (System.currentTimeMillis() / 1000L));
            mySharedEditor.apply();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    // Kontrola připojení
    private void displayData() {
        final Handler handler = new Handler();
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        ConnectivityManager cn = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo nf = cn.getActiveNetworkInfo();
                        if (nf != null && nf.isConnected() == true) {

                        } else {
                            if (activeLoE) {
                                Toast.makeText(getApplicationContext(), "Zkontrolujte připojení k internetu", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        };
        timer.schedule(task, 0, 5000);  // interval of one minute


    }

    // Update list view
    private void setRepeatingAsyncTask() {

        final Handler handler = new Handler();
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            ListViewTask jsonTask = new ListViewTask(login, password);
                            jsonTask.execute();
                        } catch (Exception e) {
                            // error, do something
                        }
                    }
                });
            }
        };

        timer.schedule(task, 0, 5000);  // interval of one minute
    }

    /**
     * Classa na AsyncTask - kontrola údajů api
     */
    public class ListViewTask extends AsyncTask<Void, Void, Boolean> {

        private final String name;
        private final String password;
        private Boolean logedIn = false;

        ListViewTask(String name, String password){
            this.name = name;
            this.password = password;
        }

        // Získání údajů z API
        @Override
        protected Boolean doInBackground(Void... voids) {
            ApiClient userClient = retrofit.create(ApiClient.class);

            String base = name + ":" + password;
            String authHeader = "Basic " + Base64.encodeToString(base.getBytes(), Base64.NO_WRAP);
            Call<User> call = userClient.getUser(authHeader);

            try {
                Response<User> response = call.execute();
                if(response.isSuccessful()){
                    user = response.body();
                    user.setName(name);
                    logedIn = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return logedIn;
        }

        @Override
        protected void onPostExecute(final Boolean success){

            if(success){
                // Vyplnění layoutu akcema
                adapter = new EventAdapter(ListOfEventsActivity.this,R.layout.list_event_layout, user.getEvents());
                lv = (ListView)findViewById(R.id.listView1);
                lv.setAdapter(adapter);
                lv.setOnItemClickListener(new ListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        Event event = (Event) adapterView.getItemAtPosition(i);

                        Intent intent = new Intent(view.getContext(), BarcodeReaderActivity.class);
                        startActivity(intent);
                    }
                });
            }else{
                // Something wrong
            }

        }

        @Override
        protected void  onCancelled() {
        }

    }
}
