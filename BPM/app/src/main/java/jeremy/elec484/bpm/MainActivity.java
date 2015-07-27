package jeremy.elec484.bpm;

import android.media.MediaPlayer;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity
        implements TrackFragment.OnFragmentInteractionListener{


    private static MediaPlayer mMediaPlayer;

    public static MediaPlayer getMediaPlayer(){
        return mMediaPlayer;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start off on the main page
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, new MainActivityFragment())
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMediaPlayer = new MediaPlayer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * For the "Select Track" button
     * @param v The button
     */
    public void onSelectTrackClick(View v) {
        Log.d("MainActivity", "Opening track selector...");
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, new TrackFragment())
                .addToBackStack("MainActivityFragment") //Enables back-button functionality
                .commit();
    }
    /**
     * For the "Cancel" button
     * @param v The button
     */
    public void onCancelSelectTrackClick(View v) {
        Log.d("MainActivity", "Opening track selector...");
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.popBackStack("MainActivityFragment", FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.container, new MainActivityFragment())
                .commit();
    }

    @Override
    public void onFragmentInteraction(Track track) {
        Log.d("MainActivity", "Track selection made: " + track.getName());
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.popBackStack("MainActivityFragment", FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.container, MainActivityFragment.newInstance(track))
                .commit();

        try {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.reset();
            }
            mMediaPlayer.setDataSource(track.getPath());
            mMediaPlayer.prepare();
        } catch (Exception e) {
            Log.w("MainActivity", "ERROR: "+e.getMessage());
        }

    }

}
