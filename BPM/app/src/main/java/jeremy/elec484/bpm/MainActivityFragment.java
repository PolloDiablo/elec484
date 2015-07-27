package jeremy.elec484.bpm;

import android.media.MediaPlayer;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import myjavax.sound.sampled.UnsupportedAudioFileException;

/**
 * The main screen of the application
 */
public class MainActivityFragment extends Fragment {

    private static boolean isTrackSelected = false;
    private static boolean isTrackProcessed = false;
    private static String trackName = "";
    private static String trackPath = "";

    public boolean isTrackSelected() {
        return isTrackSelected;
    }

    public boolean isTrackProcessed() {
        return isTrackProcessed;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getTrackPath() {
        return trackPath;
    }

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

        // Initially track has not been processed
        isTrackProcessed = false;

        return newFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        // "Processing..." initially hidden
        final View processingText = view.findViewById(R.id.adjustmentProcessing);
        processingText.setVisibility(View.INVISIBLE);

        SeekBar adjustmentBar  = (SeekBar)view.findViewById(R.id.adjustmentBar);
        adjustmentBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
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

                double ratioAdjustment = progress/100.0;
                if(ratioAdjustment < 1){
                    ratioAdjustment = 1.0/(2.0-ratioAdjustment);
                }

                // Update UI before for processing
                // TODO disable all interaction?
                processingText.setVisibility(View.VISIBLE);
                MainActivityFragment.this.getActivity().findViewById(R.id.adjustmentHeader).setVisibility(View.VISIBLE);
                TextView adjustmentValue = (TextView)MainActivityFragment.this.getActivity().findViewById(R.id.adjustmentValue);
                adjustmentValue.setText(String.format("%.2fx", ratioAdjustment));
                adjustmentValue.setVisibility(View.VISIBLE);

                // Stop mediaPlayer
                MediaPlayer mediaPlayer = MainActivity.getMediaPlayer();
                double savedPositionRatio;
                if (mediaPlayer.isPlaying()) {
                    savedPositionRatio = mediaPlayer.getCurrentPosition()/(double)mediaPlayer.getDuration();
                    mediaPlayer.reset();
                }else{
                    savedPositionRatio = 0;
                }

                // ========================================================================
                // Begin Processing...
                String newTrackPath;
                try{
                    newTrackPath = AudioProcessor.process(trackPath,ratioAdjustment);
                }catch(java.io.IOException e){
                    newTrackPath = trackPath;
                    Log.e("MainActivityFragment","Could not process audio");
                    Log.e("MainActivityFragment",e.getMessage());
                } catch (UnsupportedAudioFileException e) {
                    newTrackPath = trackPath;
                    Log.e("MainActivityFragment",e.getMessage());
                }

                // ========================================================================

                // Update UI after processing
                processingText.setVisibility(View.INVISIBLE);
                isTrackProcessed = true;
                updateVisibilityOfTrackOptions(MainActivityFragment.this.getActivity());

                // Restart media player
                try {
                    mediaPlayer.setDataSource(newTrackPath);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    mediaPlayer.seekTo((int)(savedPositionRatio*mediaPlayer.getDuration()));
                } catch (Exception e) {
                    Log.w("MainActivityFragment", "ERROR: " + e.getMessage());
                }

            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
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

        SeekBar seek  = (SeekBar)view.findViewById(R.id.seekBar);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Progress is returned as a value between 0 -> 100
                // Seek is done to a millisecond value
                // So we need to convert the seek bar % to a time value

                // Get duration of track in milliseconds
                int songLength = MainActivity.getMediaPlayer().getDuration();
                int tickSize = songLength / 100;

                MainActivity.getMediaPlayer().seekTo(tickSize * progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateVisibilityOfTrackOptions(getActivity());
        updateTrackTitle(getActivity());
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

        if(isTrackProcessed){
            temp =  View.VISIBLE;
        }else{
            temp = View.INVISIBLE;
        }
        activity.findViewById(R.id.adjustmentHeader).setVisibility(temp);
        activity.findViewById(R.id.adjustmentValue).setVisibility(temp);

        activity.findViewById(R.id.play).setVisibility(temp);
        activity.findViewById(R.id.pause).setVisibility(temp);
        activity.findViewById(R.id.seekText).setVisibility(temp);
        activity.findViewById(R.id.seekBar).setVisibility(temp);
    }


    public static void updateTrackTitle(android.support.v4.app.FragmentActivity activity){
        TextView t = (TextView)(activity.findViewById(R.id.trackName));
        t.setText(trackName);
    }

}
