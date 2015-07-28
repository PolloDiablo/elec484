package jeremy.elec484.bpm;

import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The main screen of the application
 */
public class MainActivityFragment extends Fragment {

    private static boolean isTrackSelected = false;
    private static double adjustmentRatio = 1.0;
    private static String trackName = "";
    private static String trackPath = "";

    private static AsyncTask myAsyncTask;
    private static SeekBar seekBar;

    Handler handler;

    public MainActivityFragment() {
        // Use the default constructor on first run (no track has been selected yet)
        //  Or on cancel from the track select screen
        //      - Case 1: no prior track has been selected ... still no track has been selected
        //      - Case 2: a prior track has been selected ... it will remain
    }

    public static MainActivityFragment newInstance(Track track) {
        MainActivityFragment newFragment = new MainActivityFragment();

        isTrackSelected = true;
        trackName = track.getName();
        trackPath = track.getPath();

        return newFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        TextView adjustmentValue = (TextView)view.findViewById(R.id.adjustmentValue);
        adjustmentValue.setText(String.format("%.2fx", 1.0)); // Initial value 1.0

        SeekBar adjustmentBar  = (SeekBar)view.findViewById(R.id.adjustmentBar);
        adjustmentBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Progress is returned as a value between 0 -> 200
                /*
                We actually  read a linear scale...
                [0, 0.2, 0.4, 0.6, 0.8, 1 , 1.2, 1.4, 1.6, 1.8, 2]

                convert:
                if(input < 1){
                    input = 1/(2-input);
                }

                Turns into...
                [0.5, 0.55*, 0.625, 0.71426, 0.83333, 1, 1.2, 1.4, 1.6, 1.8, 2]
                */

                double playbackSpeedChange = progress / 100.0;
                if (playbackSpeedChange < 1) {
                    playbackSpeedChange = 1.0 / (2.0 - playbackSpeedChange);
                }

                // adjustmentRatio directly translates into the difference in window size
                // => 2.0x playbackSpeedChange means 0.5 adjustmentRatio
                adjustmentRatio = 1.0 / playbackSpeedChange;

                // Update the adjustmentValue text
                TextView adjustmentValue = (TextView) MainActivityFragment.this.getActivity().findViewById(R.id.adjustmentValue);
                adjustmentValue.setText(String.format("%.2fx", playbackSpeedChange));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // List for clicks on the 'Process' button
        Button process  = (Button)view.findViewById(R.id.adjustmentProcess);
        process.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                // Begin Processing...
                myAsyncTask = new ProcessAudio(trackPath, adjustmentRatio).execute();
            }
        });



        // Note the stop, play, pause, and seek buttons should only be visible if playback is possible
        Button play  = (Button)view.findViewById(R.id.play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if(!MainActivity.getMediaPlayer().isPlaying()){
                    MainActivity.getMediaPlayer().start();
                }
            }
        });

        Button pause  = (Button)view.findViewById(R.id.pause);
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if(MainActivity.getMediaPlayer().isPlaying()){
                    MainActivity.getMediaPlayer().pause();
                }
            }
        });

        seekBar  = (SeekBar)view.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Progress is returned as a value between 0 -> 1000
                // Seek is done to a millisecond value
                // So we need to convert the seek bar % to a time value

                // Get duration of track in milliseconds
                MediaPlayer mediaPlayer = MainActivity.getMediaPlayer();
                int songLength = mediaPlayer.getDuration();
                int tickSize = songLength / 1000;

                mediaPlayer.seekTo(tickSize * seekBar.getProgress());
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateVisibilityOfTrackOptions(getActivity());
        updateTrackTitle(getActivity());

        // Have a thread periodically (every 0.1s) update the seek bar
        handler = new Handler();
        handler.removeCallbacks(moveSeekBarThread);
        handler.postDelayed(moveSeekBarThread, 100);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(myAsyncTask != null){
            myAsyncTask.cancel(true);
        }
        myAsyncTask = null;

        handler.removeCallbacks(moveSeekBarThread);
    }

    public static void updateVisibilityOfTrackOptions(android.support.v4.app.FragmentActivity activity){
        int temp;
        if(isTrackSelected){
            temp =  View.VISIBLE;
        }else{
            temp = View.INVISIBLE;
        }
        activity.findViewById(R.id.trackNameHeader).setVisibility(temp);
        activity.findViewById(R.id.trackName).setVisibility(temp);
        activity.findViewById(R.id.adjustmentBar).setVisibility(temp);
        activity.findViewById(R.id.trackNameHeader).setVisibility(temp);
        activity.findViewById(R.id.trackName).setVisibility(temp);

        activity.findViewById(R.id.adjustmentPrompt).setVisibility(temp);
        activity.findViewById(R.id.adjustmentBar).setVisibility(temp);
        activity.findViewById(R.id.adjustmentLow).setVisibility(temp);
        activity.findViewById(R.id.adjustmentHigh).setVisibility(temp);
        activity.findViewById(R.id.adjustmentHeader).setVisibility(temp);
        activity.findViewById(R.id.adjustmentValue).setVisibility(temp);
        activity.findViewById(R.id.adjustmentProcess).setVisibility(temp);

        activity.findViewById(R.id.play).setVisibility(temp);
        activity.findViewById(R.id.pause).setVisibility(temp);
        activity.findViewById(R.id.seekText).setVisibility(temp);
        activity.findViewById(R.id.seekBar).setVisibility(temp);
    }


    public static void updateTrackTitle(android.support.v4.app.FragmentActivity activity){
        TextView t = (TextView)(activity.findViewById(R.id.trackName));
        t.setText(trackName);
    }

    private class ProcessAudio extends AsyncTask<Void, Integer, String> {
        private String origtpath;
        private String newtpath;
        private double adjustmentRatio;
        private double savedPositionRatio;
        ProcessAudio(String origtpath, double adjustmentRatio){
            super();
            this.origtpath = origtpath;
            this.adjustmentRatio = adjustmentRatio;
        }

        @Override
        protected String doInBackground(Void... urls) {
            long startTime = System.currentTimeMillis();

            try{
                newtpath = AudioProcessor.process(trackPath,adjustmentRatio);
            }catch(java.io.IOException e){
                newtpath = origtpath;
                Log.e("MainActivityFragment","ERROR: could not process audio");
                Log.e("MainActivityFragment",e.getMessage());
            }

            long stopTime = System.currentTimeMillis();
            long elapsedTime = stopTime - startTime;
            Log.d("MainActivityFragment","Processing Time: " + elapsedTime/1000.0 + " seconds");
            Log.d("MainActivityFragment", "Path being used " + newtpath);

            return ""; // Should be able to remove this...
        }

        @Override
        protected void onPreExecute() {
            // Update UI before for processing
            updateVisibilityOfTrackOptions(MainActivityFragment.this.getActivity());

            // Update text of 'Process' button to 'Processing'
            Button processButton  = (Button)MainActivityFragment.this.getActivity().findViewById(R.id.adjustmentProcess);
            processButton.setText(R.string.adjustment_processing);

            // Disable all interactions
            RelativeLayout viewGroup = (RelativeLayout) MainActivityFragment.this.getActivity().findViewById(R.id.relativeLayout);
            int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View view = viewGroup.getChildAt(i);
                view.setEnabled(false);
            }

            // Stop mediaPlayer
            MediaPlayer mediaPlayer = MainActivity.getMediaPlayer();
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                savedPositionRatio = mediaPlayer.getCurrentPosition()/(double)mediaPlayer.getDuration();
            }else{
                savedPositionRatio = 0;
            }
            mediaPlayer.reset();
        }

        @Override
        protected void onPostExecute(String s) {
            // Update text of 'Processing' button to 'Process'
            Button processButton  = (Button)MainActivityFragment.this.getActivity().findViewById(R.id.adjustmentProcess);
            processButton.setText(R.string.adjustment_process);

            // Re-enable all interactions
            RelativeLayout viewGroup = (RelativeLayout) MainActivityFragment.this.getActivity().findViewById(R.id.relativeLayout);
            int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View view = viewGroup.getChildAt(i);
                view.setEnabled(true);
            }

            // Restart media player
            MediaPlayer mediaPlayer = MainActivity.getMediaPlayer();
            try {
                mediaPlayer.setDataSource(newtpath);
                mediaPlayer.prepare();
                mediaPlayer.seekTo((int) (savedPositionRatio * mediaPlayer.getDuration()));
            } catch (Exception e) {
                Log.w("MainActivityFragment", "ERROR: " + e.getMessage());
            }
        }
    }

    private Runnable moveSeekBarThread = new Runnable() {

        public void run() {
            MediaPlayer mediaPlayer = MainActivity.getMediaPlayer();
            if(mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {


                    // SeekBar has a value between 0 -> 1000
                    int progressPercentage = (int) (1000 * (mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration()));
                    seekBar.setProgress(progressPercentage);
                }
            }
            handler.postDelayed(this, 100); //Looping the thread after 0.1 seconds
        }
    };


}
