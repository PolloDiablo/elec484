package jeremy.elec484.bpm;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a list of wav files on the device in a selectable listView
 */
public class TrackFragment extends Fragment implements AbsListView.OnItemClickListener {

    private OnFragmentInteractionListener mListener;
    public static List<Track> tracks = new ArrayList<>();

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private ListAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Clear list of tracks
        tracks.clear();

        /** For reference:
        http://z4android.blogspot.ca/2011/06/displaying-list-of-music-files-stored.html


        Query Parameters:
        http://developer.android.com/reference/android/content/ContentResolver.html
        Uri uri
            The URI, using the content:// scheme, for the content to retrieve.
        String[] projection
            A list of which columns to return.
            Passing null will return all columns, which is inefficient.
        String selection
            A filter declaring which rows to return, formatted as an SQL WHERE clause.
            Passing null will return all rows for the given URI.
        String[] selectionArgs
            The args (if any) for the above selection. Specified by "?s" in selection.
        String sortOrder
            How to order the rows, formatted as an SQL ORDER BY clause.
            Passing null will use the default sort order, which may be unordered.
         */

        // Get a list of all audio files
        final Cursor mCursor = getActivity().getApplicationContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,        // uri (all songs on SD card)
                new String[]{   MediaStore.Audio.Media.TITLE,       // projection (title and path)
                        MediaStore.Audio.Media.DATA},
                null,//MediaStore.Audio.Media.TITLE,                // selection
                null,                                               // selectionArgs
                "LOWER(" + MediaStore.Audio.Media.TITLE + ") ASC"); // sortOrder

        // Iterate through the list, keep only the wav files
        if (mCursor.moveToFirst()) {
            do {
                // Get the title and the path
                String name = mCursor.getString(0);
                String path = mCursor.getString(1);

                // Check if its a .wav file
                if(mCursor.getString(1).endsWith(".wav")) {
                    Log.d("TrackFragment", "Adding track to list: "+ mCursor.getString(1));
                    tracks.add(new Track(name, path));
                }

            } while (mCursor.moveToNext());
        }
        mCursor.close();

        // Display the list of tracks
        mAdapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_1, android.R.id.text1, tracks);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_track, container, false);

        // Set the adapter
        /*
            The fragment's ListView/GridView.
        */
        AbsListView mListView = (AbsListView) view.findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);

        // Set OnItemClickListener so we can be notified on item clicks
        mListView.setOnItemClickListener(this);

        // Set the message for an empty list
        TextView emptyView = (TextView)view.findViewById(android.R.id.empty);
        emptyView.setText(getString(R.string.no_tracks));
        mListView.setEmptyView(emptyView);

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (null != mListener) {
            // Notify the active callbacks interface (the activity, if the
            // fragment is attached to one) that an item has been selected.
            mListener.onFragmentInteraction(tracks.get(position));
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Track track);
    }

}
