package jeremy.elec484.bpm;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A placeholder fragment containing a simple view.
 */

public class MainActivityFragment extends Fragment {

    private static boolean isTrackSelected = false;
    private static String trackName = "";
    private static String trackPath = "";

    public boolean isTrackSelected() {
        return isTrackSelected;
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

        return newFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
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
        activity.findViewById(R.id.adjustmentHeader).setVisibility(temp);
        activity.findViewById(R.id.adjustment).setVisibility(temp);

        activity.findViewById(R.id.stop).setVisibility(temp);
        activity.findViewById(R.id.play).setVisibility(temp);
        activity.findViewById(R.id.pause).setVisibility(temp);
        activity.findViewById(R.id.seekBar).setVisibility(temp);
    }


    public static void updateTrackTitle(android.support.v4.app.FragmentActivity activity){
        TextView t = (TextView)(activity.findViewById(R.id.trackName));
        t.setText(trackName);
    }

}
