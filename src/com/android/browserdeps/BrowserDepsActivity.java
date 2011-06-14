package com.android.browserdeps;

import java.net.URL;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
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
import android.util.Log;
import android.view.Gravity;
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
	
	private SharedPreferences sp = null;
	
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
				} else if(pref.getKey().equals("updates"))
					CheckForUpdates();
				
				return true;
			}
		};
		
		getPreferenceScreen().findPreference("setasdef").setOnPreferenceClickListener(onclick);
		getPreferenceScreen().findPreference("updates").setOnPreferenceClickListener(onclick);
    }
    
    /** Вызывается при запуске приложения */
    @Override
    public void onStart() {
        super.onStart();
        
        sp = getPreferenceManager().getSharedPreferences();
        
        /* Проверяем интент
         * Если послан собой, то завершаемся
         * Если чем-то другим, то запускаем нужный браузер с интентом
         * Если интента нет, то показываем настройки */
        if(getIntent().getBooleanExtra("by self", false) == true)
        	finish();
        
        if(getIntent().getData() != null) {
        	SharedPreferences sp = getPreferenceManager().getSharedPreferences();

        	Intent intent = new Intent(Intent.ACTION_VIEW, getIntent().getData());
        	String browserPackage = null;
        	
        	int conType = GetActiveConnectionType(); //получаем тип соединения
        	        	
        	if(conType == TYPE_MOBILE ||
        			conType == TYPE_MOBILE_DUN || 
        			conType == TYPE_MOBILE_HIPRI || 
        			conType == TYPE_MOBILE_MMS ||
        			conType == TYPE_MOBILE_SUPL)
        		browserPackage = sp.getString("mobile", null);	
        	
        	else if(conType == TYPE_WIFI)
        		browserPackage = sp.getString("wifi", null);
        	
        	else if(conType == TYPE_WIMAX)
        		browserPackage = sp.getString("wimax", null);
        	
        	else 
        		browserPackage = sp.getString("offline", null);
        	
        	if(browserPackage != null) {
	        	intent.setPackage(browserPackage);
	        	
	        	startActivity(intent);
	        	finish();
        	}
        	else
        		Toast.makeText(getBaseContext(), getString(R.string.nobrowser), Toast.LENGTH_LONG).show();
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
    	boolean debug = sp.getBoolean("debug", false);
    	
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netinfo = cm.getActiveNetworkInfo();
        
        int res;
        
        if(netinfo != null) 
        	res = netinfo.getType();
        else
        	res = TYPE_NOT_CONNECTED;
        
    	if(debug) {
    		Toast toast = Toast.makeText(getBaseContext(), "Connection type: " + Integer.toString(res), Toast.LENGTH_LONG);
    		toast.setGravity(Gravity.CENTER, 0, 0);
    		toast.show();
    	}
    	
    	return res;
    }
    
    /** Проверка обновлений */
    private void CheckForUpdates() {
    	boolean debug = sp.getBoolean("debug", false);
    	
    	try {
    		Toast.makeText(getBaseContext(), "getString(R.string.checkingupd)", 100).show();
    		
    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    		DocumentBuilder db = dbf.newDocumentBuilder();
    		Document doc = db.parse(new URL("https://raw.github.com/nd0ut/BrowserDeps/master/AndroidManifest.xml").openStream());
			
    		Node node = doc.getFirstChild();
    		NamedNodeMap attributes = node.getAttributes();
    		Node code = attributes.getNamedItem("android:versionCode");
    		
    		int upd_code = Integer.parseInt(code.getNodeValue());
    		int cur_code = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
    		
        	if(debug) 
        		Toast.makeText(getBaseContext(), "Current vercode: " + Integer.toString(cur_code) + 
        				"\nLast vercode: " + Integer.toString(upd_code), Toast.LENGTH_LONG).show();
    		
    		if(upd_code > cur_code) {
    			Toast.makeText(getBaseContext(), getString(R.string.newver), Toast.LENGTH_SHORT).show();
    			
    			Node ver = attributes.getNamedItem("android:versionName");
    			String upd_ver = ver.getNodeValue();
    			
    	        Intent intent = new Intent();
    	        intent.addCategory(Intent.CATEGORY_BROWSABLE);
    	        intent.addCategory(Intent.CATEGORY_DEFAULT);
    	        intent.setAction(Intent.ACTION_VIEW);
    	        intent.setData(new Uri.Builder().build().parse("https://github.com/downloads/nd0ut/BrowserDeps/BrowserDeps" + upd_ver + ".apk"));
    	        
    	        startActivity(intent);    			
    		}
    		else
    			Toast.makeText(getBaseContext(), getString(R.string.nonewver), Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Log.e(getApplicationInfo().name, e.toString());
			Toast.makeText(getBaseContext(), getString(R.string.conerror), Toast.LENGTH_LONG).show();
		}   	
    }
}