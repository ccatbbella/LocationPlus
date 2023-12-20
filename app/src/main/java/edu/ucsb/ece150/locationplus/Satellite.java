package edu.ucsb.ece150.locationplus;

/*
 * This class is provided as a way for you to store information about a single satellite. It can
 * be helpful if you would like to maintain the list of satellites using an ArrayList (i.e.
 * ArrayList<Satellite>). As in Homework 3, you can then use an Adapter to update the list easily.
 *
 * You are not required to implement this if you want to handle satellite information in using
 * another method.
 */
public class Satellite {
    private final String[] CONSTELLATIONS = {
            "UNKNOWN", "GPS", "SBAS", "GLONASS", "QZSS", "BEIDOU", "GALILEO", "IRNSS"
    };
    private int mIndex;
    private float mAzimuth;
    private float mCarrierFrequency;
    private float mCn0;
    private int mConstellationType;
    private float mElevation;
    private int mSvid;

    public Satellite(int index, float azimuth, float carrierFrequency, float cn0, int constellationType, float elevation, int svid) {
        this.mIndex = index;
        this.mAzimuth = azimuth;
        this.mCarrierFrequency = carrierFrequency;
        this.mCn0 = cn0;
        this.mConstellationType = constellationType;
        this.mElevation = elevation;
        this.mSvid = svid;
    }

    // When the Adapter tries to assign names to items in the ListView, it calls the toString() method of the objects in the ArrayList
    @Override
    public String toString() {
        return ("Satellite " + mIndex);
    }
    public String getSatelliteInformation() {
        return (new StringBuilder(""))
                .append("Azimuth: ").append(mAzimuth).append(" °\n")
                .append("Elevation: ").append(mElevation).append(" °\n\n")
                .append("Carrier Frequency: ").append(mCarrierFrequency).append(" Hz\n")
                .append("C/N0: ").append(mCn0).append(" dB Hz\n\n")
                .append("Constellation: ").append(CONSTELLATIONS[mConstellationType]).append("\n")
                .append("SVID: ").append(mSvid)
                .toString();
    }
}
