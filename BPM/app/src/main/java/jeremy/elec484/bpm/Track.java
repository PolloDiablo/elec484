package jeremy.elec484.bpm;

/**
 * Each Track stores a displayName and path (to the audio file)
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
