package file;

import agent.Rescuer;
import map.CityMap;
import map.Hospital;
import victim.Injured;

import java.util.List;

// --------------------
// لایه: File I/O Layer
// --------------------
// این کلاس اطلاعات کلی وضعیت فعلی بازی رو نگه می‌داره
// برای ذخیره‌سازی و بارگذاری استفاده می‌شه
public class GameState {

    private CityMap map;                    // نقشه شهر
    private List<Rescuer> rescuers;         // لیست نجات‌دهنده‌ها
    private List<Injured> victims;          // لیست مجروح‌ها
    private List<Hospital> hospitals;       // لیست بیمارستان‌ها
    private int score;                      // امتیاز فعلی بازی

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

    public CityMap getMap() {
        return map;
    }

    public List<Rescuer> getRescuers() {
        return rescuers;
    }

    public List<Injured> getVictims() {
        return victims;
    }

    public List<Hospital> getHospitals() {
        return hospitals;
    }

    public int getScore() {
        return score;
    }

    public void setMap(CityMap map) {
        this.map = map;
    }

    public void setRescuers(List<Rescuer> rescuers) {
        this.rescuers = rescuers;
    }

    public void setVictims(List<Injured> victims) {
        this.victims = victims;
    }

    public void setHospitals(List<Hospital> hospitals) {
        this.hospitals = hospitals;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
