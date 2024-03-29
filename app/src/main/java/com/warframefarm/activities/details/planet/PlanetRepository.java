package com.warframefarm.activities.details.planet;

import static com.warframefarm.database.WarframeFarmDatabase.MISSION_FACTION;
import static com.warframefarm.database.WarframeFarmDatabase.MISSION_NAME;
import static com.warframefarm.database.WarframeFarmDatabase.MISSION_OBJECTIVE;
import static com.warframefarm.database.WarframeFarmDatabase.MISSION_PLANET;
import static com.warframefarm.database.WarframeFarmDatabase.MISSION_TABLE;
import static com.warframefarm.database.WarframeFarmDatabase.MISSION_TYPE;
import static com.warframefarm.database.WarframeFarmDatabase.M_REWARD_DROP_CHANCE;
import static com.warframefarm.database.WarframeFarmDatabase.M_REWARD_MISSION;
import static com.warframefarm.database.WarframeFarmDatabase.M_REWARD_RELIC;
import static com.warframefarm.database.WarframeFarmDatabase.M_REWARD_ROTATION;
import static com.warframefarm.database.WarframeFarmDatabase.M_REWARD_TABLE;
import static com.warframefarm.database.WarframeFarmDatabase.R_REWARD_COMPONENT;
import static com.warframefarm.database.WarframeFarmDatabase.R_REWARD_RELIC;
import static com.warframefarm.database.WarframeFarmDatabase.R_REWARD_TABLE;
import static com.warframefarm.database.WarframeFarmDatabase.TYPE_ARCHWING;
import static com.warframefarm.database.WarframeFarmDatabase.TYPE_EMPYREAN;
import static com.warframefarm.database.WarframeFarmDatabase.TYPE_NORMAL;
import static com.warframefarm.database.WarframeFarmDatabase.USER_COMPONENT_ID;
import static com.warframefarm.database.WarframeFarmDatabase.USER_COMPONENT_OWNED;
import static com.warframefarm.database.WarframeFarmDatabase.USER_COMPONENT_TABLE;

import android.app.Application;
import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.warframefarm.AppExecutors;
import com.warframefarm.database.Mission;
import com.warframefarm.database.MissionDao;
import com.warframefarm.database.MissionReward;
import com.warframefarm.database.Planet;
import com.warframefarm.database.PlanetDao;
import com.warframefarm.database.WarframeFarmDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class PlanetRepository {

    private final PlanetDao planetDao;
    private final MissionDao missionDao;

    private final Executor backgroundThread, mainThread;

    private final MutableLiveData<Planet> planet = new MutableLiveData<>();
    private final MutableLiveData<List<Integer>> typeList = new MutableLiveData<>(new ArrayList<>());

    private String search = "";
    private final MutableLiveData<Boolean> relicFilter = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> typeFilter = new MutableLiveData<>(-1);

    private final MutableLiveData<List<Mission>> missions = new MutableLiveData<>(new ArrayList<>());

    public PlanetRepository(Application application) {
        WarframeFarmDatabase database = WarframeFarmDatabase.getInstance(application);
        planetDao = database.planetDao();
        missionDao = database.missionDao();

        AppExecutors executors = new AppExecutors();
        backgroundThread = executors.getBackgroundThread();
        mainThread = executors.getMainThread();
    }

    public void setPlanet(String name) {
        backgroundThread.execute(() -> {
            Planet p = planetDao.getPlanet(name);
            mainThread.execute(() -> {
                planet.setValue(p);
                setTypeList();
                updateMissions();
            });
        });
    }

    public LiveData<Planet> getPlanet() {
        return planet;
    }

    public void setSearch(String search) {
        this.search = search;
        updateMissions();
    }

    public void setTypeList() {
        backgroundThread.execute(() -> {
            List<Integer> types = planetDao.getPlanetMissionTypes(planet.getValue().getName());
            mainThread.execute(() -> typeList.setValue(types));
        });
    }

    public LiveData<List<Integer>> getTypeList() {
        return typeList;
    }

    public void switchRelicFilter() {
        relicFilter.setValue(!relicFilter.getValue());
        updateMissions();
    }

    public LiveData<Boolean> getRelicFilter() {
        return relicFilter;
    }

    public void setTypeFilter(int type) {
        int currentType = typeFilter.getValue();
        if (currentType != type)
            typeFilter.setValue(type);
        else
            typeFilter.setValue(-1);
        updateMissions();
    }

    public MutableLiveData<Integer> getTypeFilter() {
        return typeFilter;
    }

    public LiveData<List<Mission>> getMissions() {
        return missions;
    }

    public void updateMissions() {
        backgroundThread.execute(() -> {
            Planet p = planet.getValue();
            if (p == null)
                return;

            boolean relicFilter = this.relicFilter.getValue();
            int typeFilter = this.typeFilter.getValue();

            String NEEDED_MISSION_REWARDS = "NEEDED_MISSION_REWARDS";

            String queryString ="WITH " + NEEDED_MISSION_REWARDS + " AS (" +
                        " SELECT DISTINCT " + M_REWARD_TABLE + ".*" +
                        " FROM " + MISSION_TABLE +
                        " LEFT JOIN " + M_REWARD_TABLE + " ON " + M_REWARD_MISSION + " == " + MISSION_NAME +
                        " LEFT JOIN " + R_REWARD_TABLE + " ON " + R_REWARD_RELIC + " == " + M_REWARD_RELIC +
                        " LEFT JOIN " + USER_COMPONENT_TABLE + " ON " + USER_COMPONENT_ID + " == " + R_REWARD_COMPONENT +
                        " WHERE " + MISSION_PLANET + " == '" + p.getName() + "'" +
                        " AND " + USER_COMPONENT_OWNED + " == 0" +
                    ")" +
                    " SELECT " + MISSION_TABLE + ".*, COALESCE(" + M_REWARD_RELIC + ", '') AS " + M_REWARD_RELIC +
                    ", COALESCE(" + M_REWARD_ROTATION + ", '') AS " + M_REWARD_ROTATION +
                    ", COALESCE(SUM(" + M_REWARD_DROP_CHANCE + "), 0) AS " + M_REWARD_DROP_CHANCE +
                    " FROM " + MISSION_TABLE +
                    " LEFT JOIN " + NEEDED_MISSION_REWARDS + " ON " + M_REWARD_MISSION + " == " + MISSION_NAME +
                    " WHERE " + MISSION_PLANET + " == '" + p.getName() + "'" +
                    (typeFilter == -1 ?
                            "" :
                            " AND " + MISSION_TYPE + " == " + typeFilter
                    ) +
                    (search.isEmpty() ?
                        "" :
                        " AND (" + MISSION_NAME + " LIKE \"" + search + "%\"" +
                            " OR " + MISSION_OBJECTIVE + " LIKE \"" + search + "%\"" +
                            " OR " + MISSION_TYPE + " == (CASE" +
                                " WHEN 'Normal' LIKE \"" + search + "%\" THEN " + TYPE_NORMAL +
                                " WHEN 'Archwing' LIKE \"" + search + "%\" THEN " + TYPE_ARCHWING +
                                " WHEN 'Empyrean' LIKE \"" + search + "%\"" +
                                " OR 'Railjack' LIKE \"" + search + "%\" THEN " + TYPE_EMPYREAN +
                            " END)" +
                        " OR " + MISSION_FACTION + " LIKE \"" + search + "%\")"
                    ) +
                    (relicFilter ? " AND " + M_REWARD_RELIC + " != ''" : "") +
                    " GROUP BY " + MISSION_NAME + ", " + M_REWARD_ROTATION +
                    " ORDER BY " + M_REWARD_DROP_CHANCE + " == 0 DESC, " + MISSION_TYPE + ", " + MISSION_NAME + ", " + M_REWARD_ROTATION;

            List<Mission> missionList = new ArrayList<>();

            SimpleSQLiteQuery query = new SimpleSQLiteQuery(queryString);
            Cursor cursor = missionDao.getMissions(query);
            if (cursor != null) {
                Mission mission = null;
                String mission_planet, mission_name, mission_objective, mission_faction;
                int mission_type;

                MissionReward reward;
                String reward_relic, reward_rotation;
                double reward_rarity;

                int col_planet = cursor.getColumnIndex(MISSION_PLANET),
                        col_mission = cursor.getColumnIndex(MISSION_NAME),
                        col_objective = cursor.getColumnIndex(MISSION_OBJECTIVE),
                        col_faction = cursor.getColumnIndex(MISSION_FACTION),
                        col_type = cursor.getColumnIndex(MISSION_TYPE),
                        col_relic = cursor.getColumnIndex(M_REWARD_RELIC),
                        col_rotation = cursor.getColumnIndex(M_REWARD_ROTATION),
                        col_rarity = cursor.getColumnIndex(M_REWARD_DROP_CHANCE);

                while (cursor.moveToNext()) {
                    mission_name = cursor.getString(col_mission);
                    if (mission == null || !mission.getName().equals(mission_name)) {
                        mission_planet = cursor.getString(col_planet);
                        mission_objective = cursor.getString(col_objective);
                        mission_faction = cursor.getString(col_faction);
                        mission_type = cursor.getInt(col_type);
                        mission = new Mission(mission_name, mission_planet, mission_objective, mission_faction, mission_type);
                        missionList.add(mission);
                    }

                    reward_relic = cursor.getString(col_relic);
                    if (reward_relic.equals(""))
                        continue;
                    reward_rotation = cursor.getString(col_rotation);
                    reward_rarity = cursor.getDouble(col_rarity);
                    reward = new MissionReward(mission_name, reward_relic, reward_rotation, reward_rarity);
                    mission.addMissionReward(reward);
                }
                cursor.close();
            }

            mainThread.execute(() -> missions.setValue(missionList));
        });
    }
}
