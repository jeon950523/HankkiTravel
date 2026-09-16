package kr.hankkitravel.trip.model;

public final class TripProblem extends RuntimeException {
    private final String code;
    private final int status;
    public TripProblem(String code, int status) { super(code); this.code = code; this.status = status; }
    public String code() { return code; }
    public int status() { return status; }
    public static TripProblem invalid(String code) { return new TripProblem(code, 400); }
    public static TripProblem missing(String code) { return new TripProblem(code, 404); }
}
