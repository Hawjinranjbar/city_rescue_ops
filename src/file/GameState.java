package file;

import agent.Rescuer;
import map.CityMap;
import map.Hospital;
import util.Position;
import victim.Injured;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * --------------------
 * لایه: File I/O Layer
 * --------------------
 * نگهدارندهٔ وضعیت کلی بازی برای ذخیره/بارگذاری.
 * - مراجع runtime (CityMap/Rescuer/Injured/Hospital) به‌صورت transient هستند
 *   تا موقع serialize خطا ندهند.
 * - یک اسنپ‌شات سبک (DTO) داخل همین کلاس نگه‌داری می‌شود که سریالایز می‌گردد.
 * - امضاهای قبلی get/set حفظ شده‌اند.
 */
public class GameState implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------
    // مراجع runtime (ذخیره نمی‌شوند)
    // ------------------------
    private transient CityMap map;              // نقشه شهر (runtime only)
    private transient List<Rescuer> rescuers;   // لیست نجات‌دهنده‌ها (runtime only)
    private transient List<Injured> victims;    // لیست مجروح‌ها (runtime only)
    private transient List<Hospital> hospitals; // لیست بیمارستان‌ها (runtime only)

    // ------------------------
    // داده‌های سریالایزشدنی (اسنپ‌شات سبک)
    // ------------------------
    private String mapPath;           // مثلاً: assets/maps/rescue_city.tmx
    private int mapWidth;
    private int mapHeight;
    private int tileWidth;
    private int tileHeight;

    private int score;                // امتیاز فعلی
    private long elapsedMillis;       // زمان سپری‌شده از شروع (اختیاری)
    private long remainingMillis;     // اگر تایمر معکوس داری

    // اسنپ‌شات عامل‌ها و مجروح‌ها و بیمارستان‌ها
    private List<RescuerDTO> rescuerSnapshot = new ArrayList<RescuerDTO>();
    private List<InjuredDTO>  victimSnapshot  = new ArrayList<InjuredDTO>();
    private List<HospitalDTO> hospitalSnapshot = new ArrayList<HospitalDTO>();

    // ------------------------
    // سازنده‌ها
    // ------------------------
    public GameState() {
        // بدون پارامتر برای Serialization
    }

    public GameState(CityMap map,
                     List<Rescuer> rescuers,
                     List<Injured> victims,
                     List<Hospital> hospitals,
                     int score) {
        this.map = map;
        this.rescuers = rescuers;
        this.victims = victims;
        this.hospitals = hospitals;
        this.score = score;
    }

    // ------------------------
    // Getter/Setter های قبلی (compat)
    // ------------------------
    public CityMap getMap() { return map; }
    public List<Rescuer> getRescuers() { return rescuers; }
    public List<Injured> getVictims() { return victims; }
    public List<Hospital> getHospitals() { return hospitals; }
    public int getScore() { return score; }

    public void setMap(CityMap map) { this.map = map; }
    public void setRescuers(List<Rescuer> rescuers) { this.rescuers = rescuers; }
    public void setVictims(List<Injured> victims) { this.victims = victims; }
    public void setHospitals(List<Hospital> hospitals) { this.hospitals = hospitals; }
    public void setScore(int score) { this.score = score; }

    // ------------------------
    // فیلدهای سریالایزشدنی + Getter/Setter
    // ------------------------
    public String getMapPath() { return mapPath; }
    public void setMapPath(String mapPath) { this.mapPath = mapPath; }

    public int getMapWidth() { return mapWidth; }
    public void setMapWidth(int mapWidth) { this.mapWidth = mapWidth; }

    public int getMapHeight() { return mapHeight; }
    public void setMapHeight(int mapHeight) { this.mapHeight = mapHeight; }

    public int getTileWidth() { return tileWidth; }
    public void setTileWidth(int tileWidth) { this.tileWidth = tileWidth; }

    public int getTileHeight() { return tileHeight; }
    public void setTileHeight(int tileHeight) { this.tileHeight = tileHeight; }

    public long getElapsedMillis() { return elapsedMillis; }
    public void setElapsedMillis(long elapsedMillis) { this.elapsedMillis = elapsedMillis; }

    public long getRemainingMillis() { return remainingMillis; }
    public void setRemainingMillis(long remainingMillis) { this.remainingMillis = remainingMillis; }

    public List<RescuerDTO> getRescuerSnapshot() { return rescuerSnapshot; }
    public void setRescuerSnapshot(List<RescuerDTO> rescuerSnapshot) { this.rescuerSnapshot = rescuerSnapshot; }

    public List<InjuredDTO> getVictimSnapshot() { return victimSnapshot; }
    public void setVictimSnapshot(List<InjuredDTO> victimSnapshot) { this.victimSnapshot = victimSnapshot; }

    public List<HospitalDTO> getHospitalSnapshot() { return hospitalSnapshot; }
    public void setHospitalSnapshot(List<HospitalDTO> hospitalSnapshot) { this.hospitalSnapshot = hospitalSnapshot; }

    // ------------------------
    // متدهای کمکی برای مدیریت اسنپ‌شات
    // ------------------------

    /**
     * آیا دادهٔ سبک برای بازیابی موجود است؟
     */
    public boolean hasSerializableSnapshot() {
        return (mapPath != null && mapPath.length() > 0)
                || (rescuerSnapshot != null && rescuerSnapshot.size() > 0)
                || (victimSnapshot != null && victimSnapshot.size() > 0);
    }

    /**
     * جایگزینی محتوای سریالایز‌شده/سبک از یک GameState دیگر.
     * برای زمانی که از دیسک بارگذاری می‌کنی و می‌خواهی state فعلی را همگام کنی.
     */
    public void replaceWith(GameState other) {
        if (other == null) return;

        // runtime refs را (در صورت نیاز) بیرون از این متد ست کن.
        this.score = other.score;
        this.elapsedMillis = other.elapsedMillis;
        this.remainingMillis = other.remainingMillis;

        this.mapPath   = other.mapPath;
        this.mapWidth  = other.mapWidth;
        this.mapHeight = other.mapHeight;
        this.tileWidth = other.tileWidth;
        this.tileHeight= other.tileHeight;

        // snapshot ها را کپی می‌کنیم
        this.rescuerSnapshot = new ArrayList<RescuerDTO>();
        if (other.rescuerSnapshot != null) {
            for (int i = 0; i < other.rescuerSnapshot.size(); i++) {
                this.rescuerSnapshot.add(other.rescuerSnapshot.get(i));
            }
        }
        this.victimSnapshot = new ArrayList<InjuredDTO>();
        if (other.victimSnapshot != null) {
            for (int i = 0; i < other.victimSnapshot.size(); i++) {
                this.victimSnapshot.add(other.victimSnapshot.get(i));
            }
        }
        this.hospitalSnapshot = new ArrayList<HospitalDTO>();
        if (other.hospitalSnapshot != null) {
            for (int i = 0; i < other.hospitalSnapshot.size(); i++) {
                this.hospitalSnapshot.add(other.hospitalSnapshot.get(i));
            }
        }
    }

    /**
     * ست‌کردن صرفِ اطلاعات سبک نقشه (برای سیو).
     */
    public void setSnapshotMapInfo(String path, int w, int h, int tw, int th) {
        this.mapPath = path;
        this.mapWidth = w;
        this.mapHeight = h;
        this.tileWidth = tw;
        this.tileHeight = th;
    }

    /**
     * افزودن یک رکورد سبکِ نجات‌دهنده به اسنپ‌شات.
     */
    public void addRescuerSnapshot(int id, int tileX, int tileY, int direction,
                                   boolean busy, boolean ambulanceMode, Integer carryingVictimId, boolean noClip) {
        if (this.rescuerSnapshot == null) this.rescuerSnapshot = new ArrayList<RescuerDTO>();
        RescuerDTO dto = new RescuerDTO();
        dto.id = id;
        dto.tileX = tileX;
        dto.tileY = tileY;
        dto.direction = direction;
        dto.busy = busy;
        dto.ambulanceMode = ambulanceMode;
        dto.carryingVictimId = carryingVictimId;
        dto.noClip = noClip;
        this.rescuerSnapshot.add(dto);
    }

    /**
     * افزودن یک رکورد سبکِ مجروح به اسنپ‌شات.
     */
    public void addVictimSnapshot(int id, int tileX, int tileY, String severity,
                                  boolean alive, boolean rescued, boolean critical, long remainingMillis) {
        if (this.victimSnapshot == null) this.victimSnapshot = new ArrayList<InjuredDTO>();
        InjuredDTO dto = new InjuredDTO();
        dto.id = id;
        dto.tileX = tileX;
        dto.tileY = tileY;
        dto.severity = severity;
        dto.alive = alive;
        dto.rescued = rescued;
        dto.critical = critical;
        dto.remainingMillis = remainingMillis;
        this.victimSnapshot.add(dto);
    }

    /**
     * افزودن یک رکورد سبکِ بیمارستان به اسنپ‌شات.
     */
    public void addHospitalSnapshot(int tileX, int tileY) {
        if (this.hospitalSnapshot == null) this.hospitalSnapshot = new ArrayList<HospitalDTO>();
        HospitalDTO dto = new HospitalDTO();
        dto.tileX = tileX;
        dto.tileY = tileY;
        this.hospitalSnapshot.add(dto);
    }

    // --------------------------------------------------------------------
    // DTOهای درونی سبک (همه Serializable هستند) – کلاس خارجی جدید اضافه نشد
    // --------------------------------------------------------------------
    public static class RescuerDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public int tileX;
        public int tileY;
        /** 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP */
        public int direction;
        public boolean busy;
        public boolean ambulanceMode;
        public Integer carryingVictimId; // null اگر کسی را حمل نمی‌کند
        public boolean noClip;
    }

    public static class InjuredDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public int tileX;
        public int tileY;
        public String severity; // "LOW","MEDIUM","CRITICAL"
        public boolean alive;
        public boolean rescued;
        public boolean critical;
        public long remainingMillis;
    }

    public static class HospitalDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        public int tileX;
        public int tileY;
    }

    // --------------------------------------------------------------------
    // (اختیاری) نمونهٔ امن از استخراج سادهٔ مختصات از Position
    // --------------------------------------------------------------------
    private static int safeX(Position p) { return p == null ? 0 : p.getX(); }
    private static int safeY(Position p) { return p == null ? 0 : p.getY(); }
}
