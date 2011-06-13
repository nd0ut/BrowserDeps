package com.android.browserdeps;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class BrowserDepsActivity extends PreferenceActivity {

	private static final int TYPE_NOT_CONNECTED = -1;
	private static final int TYPE_MOBILE = 0;
	private static final int TYPE_MOBILE_DUN  = 4;
	private static final int TYPE_MOBILE_HIPRI = 5;
	private static final int TYPE_MOBILE_MMS = 2;
	private static final int TYPE_MOBILE_SUPL  = 3;
	private static final int TYPE_WIFI = 1;
	private static final int TYPE_WIMAX = 6;
	
	private boolean PreferencesShowed = false;
	
	/** Показывает настройки */
    public void ShowPreferences() {
    	if(!PreferencesShowed) {
    		addPreferencesFromResource(R.layout.preferences);
    		PreferencesShowed = true;
    	}
    	
    	SetEntriesAndValues(new CharSequence[] {"wifi", "mobile", "wimax", "offline"}); //загоняем список браузеров
    	
    	OnPreferenceClickListener onclick = new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference pref) {		
				if(pref.getKey().equals("setasdef")) {
					/* создаем интент */
			        Intent intent = new Intent();
			        intent.addCategory(Intent.CATEGORY_BROWSABLE);
			        intent.addCategory(Intent.CATEGORY_DEFAULT);
			        intent.setAction(Intent.ACTION_VIEW);
			        intent.setData(new Uri.Builder().build().parse("http://www.google.ru"));
			        intent.putExtra("by self", true); //указывает на то, что интент послан самой программой
			        
			        getPackageManager().clearPackagePreferredActivities(getPackageName()); //очищаем браузер по-умолчанию
			        
			        startActivity(intent); //показываем диалог выбора
				}
				return true;
			}
		};
		
		getPreferenceScreen().findPreference("setasdef").setOnPreferenceClickListener(onclick);
    }
    
    /** Вызывается при запуске приложения */
    @Override
    public void onStart() {
        super.onStart();
        
        /* Проверяем интент
         * Если послан собой, то завершаемся
         * Если чем-то другим, то запускаем нужный браузер с интентом
         * Если интента нет, то показываем настройки */
        if(getIntent().getBooleanExtra("by self", false) == true)
        	finish();
        
        else if(getIntent().getData() != null) {
        	SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        	boolean debug = sp.getBoolean("debug", false);

        	Intent intent = new Intent(Intent.ACTION_VIEW, getIntent().getData());
        	String browserPackage = null;
        	
        	int conType = GetActiveConnectionType(); //получаем тип соединения
        	
        	if(debug)
        		Toast.makeText(getBaseContext(), "Connection type: " + Integer.toString(conType), Toast.LENGTH_LONG);
        	
        	if(conType == TYPE_MOBILE ||
        			conType == TYPE_MOBILE_DUN || 
        			conType == TYPE_MOBILE_HIPRI || 
        			conType == TYPE_MOBILE_MMS ||
        			conType == TYPE_MOBILE_SUPL)
        		browserPackage = sp.getString("mobile", "com.android.browser");	
        	
        	else if(conType == TYPE_WIFI)
        		browserPackage = sp.getString("wifi", "com.android.browser");
        	
        	else if(conType == TYPE_WIMAX)
        		browserPackage = sp.getString("wimax", "com.android.browser");
        	
        	else 
        		browserPackage = sp.getString("offline", "com.android.browser");
        	
        	intent.setPackage(browserPackage);
        	
        	if(debug)
        		Toast.makeText(getBaseContext(), "Package: " + browserPackage, Toast.LENGTH_LONG);
            
        	startActivity(intent);
        	finish();
        } else
        	ShowPreferences(); //показываем настройки
    }
    
    /** Заполняет список браузеров */
    private void SetEntriesAndValues(CharSequence[] keys) {
    	List<ResolveInfo> browserlist = GetBrowsersList(); //получаем лист всех браузеров
    	
    	/* массивы для применения к преференсам */
        CharSequence[] browserLabels = new CharSequence[browserlist.size() - 1];
        CharSequence[] browserPackages = new CharSequence[browserlist.size() - 1];
        
        /* заполняем массивы, исключая само приложение */
        for(int i = 0; i < browserlist.size(); i++) {
        	if(!browserlist.get(i).activityInfo.packageName.equals(getPackageName())) {
	        	browserLabels[i] = getPackageManager().getApplicationLabel(browserlist.get(i).activityInfo.applicationInfo);
	        	browserPackages[i] = browserlist.get(i).activityInfo.packageName;
        	}
        	else if(i != browserlist.size() - 1)
        		i--;
        }
        
        /* применяем к преференсам */
        for(CharSequence key : keys) {
			ListPreference lp = (ListPreference) getPreferenceManager().findPreference(key);
			lp.setEntries(browserLabels);
			lp.setEntryValues(browserPackages);
		}
    }
    
    /** Возвращает лист браузеров */
    private List<ResolveInfo> GetBrowsersList() {
    	/* создаем интент */
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(new Uri.Builder().build().parse("http://www.google.ru"));
        
        /* получаем лист браузеров */
        PackageManager pm = getPackageManager();
        List<ResolveInfo> packages = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        
        return packages;
    }

    /** Возвращает тип текущего соединения */
    private int GetActiveConnectionType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netinfo = cm.getActiveNetworkInfo();
        
        if(netinfo != null && netinfo.isConnected()) 
        	return netinfo.getType();
        else
        	return TYPE_NOT_CONNECTED;
    }
}