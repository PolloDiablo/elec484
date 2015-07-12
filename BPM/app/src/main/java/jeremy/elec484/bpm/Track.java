package jeremy.elec484.bpm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each Track stores a displayName and path (to the audio file)
 *
 * Created by Jeremy on 7/11/2015.
 */
public class Track {


    private String name;
    private String path;

    public Track(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName(){
        return name;
    }
    public String getPath(){
        return path;
    }

    @Override
    public String toString() {
        return name;
    }
}
