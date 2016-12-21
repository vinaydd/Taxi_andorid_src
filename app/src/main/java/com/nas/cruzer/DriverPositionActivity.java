package com.nas.cruzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nas.cruzer.R;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.nas.cruzer.util.GPSTracker;
import com.nas.cruzer.util.JSONParser;
import com.nas.cruzer.util.UserInfo;
import com.nas.cruzer.util.Util;

public class DriverPositionActivity extends Activity implements OnMarkerClickListener{
	public static final String getDataUrl = "http://chakron.com/demo/cruzer/getlocations.php";
	public static final String taxiRequUrl = "http://chakron.com/demo/cruzer/requesttaxi.php";

	// Google Map
	private GoogleMap googleMap;
	
	Context con;
	
	ArrayList<Driver> drivers;
	JSONParser jparser=new JSONParser();
	GPSTracker gps;

	HashMap<Marker, Driver> markers;
	ArrayList<Marker> mapMarker;

	Handler handler;
	Runnable run;
	

	Driver selectedDriver;
	Button spchBtn;
	private final int REQ_CODE_SPEECH_INPUT = 100;


	Intent alIntent; 
	AlarmManager alarmManager;
	PendingIntent appIntent;
	
	SharedPreferences sh;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.driver_position_layout);
		    
		sh=getSharedPreferences("CRUZER_PREF", MODE_PRIVATE);
		
		drivers=new ArrayList<Driver>();
		gps=new GPSTracker(this);
		con=DriverPositionActivity.this;
		spchBtn=(Button) findViewById(R.id.speechBtn);

		if(!Util.isGPSOn(this)){
			GPSTracker.showSettingsAlert(this);
		}

		try {
			// Loading map
			initilizeMap();

		} catch (Exception e) {
			e.printStackTrace();
		}

		markers=new HashMap<Marker, Driver>();
		mapMarker=new ArrayList<Marker>();

		spchBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
				intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
						RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
				intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
				intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
						getString(R.string.speech_prompt));
				try {
					startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
				} catch (ActivityNotFoundException a) {
					Toast.makeText(getApplicationContext(),
							getString(R.string.speech_not_supported),
							Toast.LENGTH_SHORT).show();
				}
			}
		});

		new GetLocations().execute();

		startUpdateCheck();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQ_CODE_SPEECH_INPUT: 
			if (resultCode == RESULT_OK && null != data) {
				ArrayList<String> result = data
						.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				String talk=result.get(0);
				Toast.makeText(con, talk, Toast.LENGTH_LONG).show();

				if((talk.contains("driver near to my location") )|| 
						(talk.contains("driver near to me"))||
						(talk.contains("nearest taxi"))||
						(talk.contains("nearest cab"))||
						(talk.contains("taxi"))||
						(talk.contains("nearest driver"))||
						(talk.contains("who is near to me"))||
						(talk.contains("nearest driver to my location"))||
						(talk.contains("nearest driver to me"))||
						(talk.contains("request nearest driver"))||
						(talk.contains("who is the nearest driver"))||
						(talk.contains("closest driver to me"))||
						(talk.contains("find the closest driver"))){
					findTheNearestDriver(null);
				}else if((talk.contains("the driver near"))||
				(talk.contains("driver near"))||
				(talk.contains("taxi near to"))||
				(talk.contains("driver near to"))||
				(talk.contains("taxi near"))||
				(talk.contains("driver at"))||
				(talk.contains("taxi at"))||
				(talk.contains("cabs near"))||
				(talk.contains("cab near"))||
				(talk.contains("cabs at"))||
				(talk.contains("cab near to"))||
				(talk.contains("cabs near to")))
								
				{
					Toast.makeText(con, talk.substring(15), Toast.LENGTH_LONG).show();
					findTheNearestDriver(talk.substring(15).trim());
				}
			}
			break;
		}
	}

	public void findTheNearestDriver(String loc){

		Location my=new Location("My");
		if(loc==null){
			my.setLatitude(gps.getLatitude()); 
			my.setLongitude(gps.getLongitude());
		}else{
			my=new Location(loc);
		}

		ArrayList<Float> distance=new ArrayList<Float>();
		for (int i = 0; i < mapMarker.size(); i++) {
			LatLng pos=mapMarker.get(i).getPosition();
			Location markLoc=new Location("Driver");
			markLoc.setLatitude(pos.latitude);
			markLoc.setLongitude(pos.longitude);
			distance.add(my.distanceTo(markLoc));
		}
		int min=0;
		float minDis=distance.get(min);
		for (int i = 0; i < distance.size(); i++) {
			if(distance.get(i)<minDis){
				minDis=distance.get(i);
				min=i;
			}
		}
		Marker m=mapMarker.get(min);
		onMarkerClick(m);
	}

	public void startUpdateCheck(){
		alIntent= new Intent(this, UpdateReceiver.class);
		appIntent = PendingIntent.getBroadcast(this,1000, alIntent,PendingIntent.FLAG_UPDATE_CURRENT);
		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

		// 1 min in millisecond= 1*60*1000; 
		alarmManager.setRepeating(AlarmManager.RTC,System.currentTimeMillis(),30*1000, appIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.usermenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.userProfileMenu:
			startActivity(new Intent(this, UserEditProfileActivity.class));
			break;
		case R.id.userDetailsMenu:
			startActivity(new Intent(this, UserRequestActivity.class));
			break;
		case R.id.useroutmneu:
			SharedPreferences.Editor edit=sh.edit();
			edit.putString("loginemail", null);
			edit.putString("loginpass", null);
			edit.putBoolean("type", false);
			edit.commit();
			
			
			startActivity(new Intent(this, LoginActivity.class));
			finish();
			break;
		}
		return false;
	}

	/**
	 * function to load map. If map is not created it will create it for you
	 * */
	private void initilizeMap() {
		if (googleMap == null) {
			googleMap = ((MapFragment) getFragmentManager().findFragmentById(
					R.id.map)).getMap();

			googleMap.setMyLocationEnabled(true);

			LatLng latLng = new LatLng(gps.getLatitude(), gps.getLongitude());
			CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 14);
			googleMap.animateCamera(cameraUpdate);

			googleMap.setOnMarkerClickListener(this);
			// check if map is created successfully or not
			if (googleMap == null) {
				Toast.makeText(getApplicationContext(),
						"Sorry! unable to create maps", Toast.LENGTH_SHORT)
						.show();
			}
		}
	}



	@Override
	public boolean onMarkerClick(Marker marker) {
		Toast.makeText(con,"Loading vehicle information..", Toast.LENGTH_SHORT).show();
		Driver dv=markers.get(marker);
		selectedDriver=dv;
		showDriverDetailsWindow(marker,dv.getId(),dv.getInfo(),dv.getCost(),dv.getNumber());
		return true;
	}

	public void showDriverDetailsWindow(Marker mark,String id, String info,String cost,String phone){

		final Dialog dialog=new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		//dialog.setTitle("Request Driver");
		dialog.setContentView(R.layout.driver_details_layout);
		dialog.setCancelable(true);

		TextView vehicleInfo=(TextView) dialog.findViewById(R.id.driverVehicleInfo);
		TextView costkm=(TextView) dialog.findViewById(R.id.driverCostPerkm);
		TextView phn=(TextView) dialog.findViewById(R.id.driverPhone);
		TextView loc=(TextView) dialog.findViewById(R.id.driverCurrentLocation);
		final EditText dropLoc=(EditText) dialog.findViewById(R.id.driverDropLoc);
		final LinearLayout reqLay=(LinearLayout) dialog.findViewById(R.id.driverSubmitLinLay);
		Button reqBtn=(Button) dialog.findViewById(R.id.driverReqBtn);
		Button cancelBtn=(Button) dialog.findViewById(R.id.driverCancelBtn);
		final RadioButton myloc = (RadioButton) dialog.findViewById(R.id.myenterloc);
		RadioButton gpsloc = (RadioButton) dialog.findViewById(R.id.mygpsloc);
		final EditText pickme=(EditText) dialog.findViewById(R.id.mypickloc);
		
		
		myloc.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				pickme.setVisibility(View.VISIBLE);	
				}
		});
		
		gpsloc.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v){
				pickme.setVisibility(View.GONE);
			}
			
		});
		


		vehicleInfo.setText(info);
		costkm.setText("Rs " +cost);
		phn.setText(phone);

		Geocoder gcd = new Geocoder(con, Locale.getDefault());
		List<Address> addresses;
		try {
			addresses = gcd.getFromLocation(mark.getPosition().latitude, mark.getPosition().longitude, 1);
			if (addresses.size() > 0) {
				String strCompleteAddress= "";
				if (addresses.size() > 0){
					for (int i=0; i<addresses.get(0).getMaxAddressLineIndex();i++)
						strCompleteAddress+= addresses.get(0).getAddressLine(i) + "\n";
					loc.setText(strCompleteAddress.trim());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		final Button selectDropLoc=(Button) dialog.findViewById(R.id.driverDropLocBtn);
		selectDropLoc.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				selectDropLoc.setVisibility(View.GONE);
				reqLay.setVisibility(View.VISIBLE);
				dropLoc.setVisibility(View.VISIBLE);
			}
		});

		reqBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
			
				if(TextUtils.isEmpty(dropLoc.getText().toString())){
					Toast.makeText(con, "Please enter the drop location", Toast.LENGTH_SHORT).show();
					return;
				}
				if (myloc.isChecked()){
					if(TextUtils.isEmpty(pickme.getText().toString())){
						Toast.makeText(con, "Please enter your pickup address", Toast.LENGTH_SHORT).show();
						return;
					}
					else{
						new RequestTaxi().execute(pickme.getText().toString(),dropLoc.getText().toString());
						
					}
				
					
				}
				else {
				Geocoder gcd = new Geocoder(con, Locale.getDefault());
				List<Address> addresses;
				gps=new GPSTracker(con);
				try {
					addresses = gcd.getFromLocation(gps.getLatitude(), gps.getLongitude(), 1);
					if (addresses.size() > 0) {
						String strCompleteAddress= "";
						if (addresses.size() > 0){
							for (int i=0; i<addresses.get(0).getMaxAddressLineIndex();i++)
								strCompleteAddress+= addresses.get(0).getAddressLine(i) + "\n";
						}
						//Toast.makeText(con, "dhk", Toast.LENGTH_LONG).show();
						new RequestTaxi().execute(strCompleteAddress,dropLoc.getText().toString());
					}
				} catch (IOException e) {
					Util.showToast(con, e.getMessage());
					new RequestTaxi().execute("",dropLoc.getText().toString());
					e.printStackTrace();
				}
				}
				dialog.cancel();
			}
		});
		

		cancelBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				dialog.cancel();
			}
		});

		dialog.show();
	}

	class GetLocations extends AsyncTask<String, String, String>{

		ProgressDialog pDialog;
		String toastText="Internet Problem";
		String regiresult="";
		int success=0;
		int error=0;
		String errmsg="Server is down";

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			pDialog=new ProgressDialog(con);
			pDialog.setMessage("Updating drivers locations. Please wait...");
			pDialog.setIndeterminate(false);
			pDialog.setCancelable(true);
			pDialog.show();
		}

		@Override
		protected String doInBackground(String... para) {

			List<NameValuePair> params=new ArrayList<NameValuePair>();
			JSONObject json=jparser.makeHttpRequest(getDataUrl, "POST", params);

			try {
				success=json.getInt("success");
				if(success==1){

					drivers=new ArrayList<Driver>();

					JSONArray sounds=json.getJSONArray("location");
					for (int i = 0; i < sounds.length(); i++) {
						JSONObject jobj=sounds.getJSONObject(i);
						Driver d=new Driver();
						d.setId(jobj.getString("id"));
						d.setName(jobj.getString("name"));
						d.setEmail(jobj.getString("email"));
						d.setNumber(jobj.getString("number"));
						d.setLatitude(jobj.getString("latitude"));
						d.setLongitude(jobj.getString("longitude"));
						d.setInfo(jobj.getString("info"));
						d.setCost(jobj.getString("cost"));
						drivers.add(d);
					}
				}

			} catch (JSONException e) {
				e.printStackTrace();
				error=1;
			}catch (Exception e) {
				error=1;
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			//Toast.makeText(MainActivity.this, s+" "+result, Toast.LENGTH_SHORT).show();
			pDialog.dismiss();

			if(error==1){
				if(Util.isConnectingToInternet(con)){
					Toast.makeText(con,"Server is down. Please try again", Toast.LENGTH_SHORT).show();
				}else{
					Util.showNoInternetDialog(con);
				}
				return;
			}

			if(success==0){
				Toast.makeText(con,"Data loading failed", Toast.LENGTH_SHORT).show();
			}else if (success==1){
				markers=new HashMap<Marker, Driver>();
				removeMarkers();

				for (int i = 0; i < drivers.size(); i++) {
					MarkerOptions mark=new MarkerOptions();
					mark.position(new LatLng(Double.parseDouble(drivers.get(i).getLatitude()),Double.parseDouble(drivers.get(i).getLongitude())));
					//mark.title(drivers.get(i).getName());
					mark.icon(BitmapDescriptorFactory.fromResource(R.drawable.taxi));
					//mark.snippet(drivers.get(i).getInfo()+","+drivers.get(i).getCost()+" Rs.pKm, Ph. "+drivers.get(i).getNumber());
					Marker m=googleMap.addMarker(mark);
					mapMarker.add(m);
					markers.put(m, drivers.get(i));
				}
				scheduleThread();
			}
		}
	}

	private void removeMarkers() {
		for (Marker m : mapMarker) {
			m.remove();
		}
		mapMarker.clear();
	}

	public void scheduleThread(){
		handler=new Handler();
		run=new Runnable() {

			@Override
			public void run() {
				// This method will be executed once the timer is over
				if(Util.isConnectingToInternet(con)){
					new GetLocations().execute();

				}else{
					Toast.makeText(con, "Internet is not active", Toast.LENGTH_SHORT).show();
				}
			}
		};
		handler.postDelayed(run, 30000);
	}

	class RequestTaxi extends AsyncTask<String, String, String>{

		ProgressDialog pDialog;
		String toastText="Internet Problem";
		String regiresult="";
		int success=0;
		int error=0;
		String msg="";

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			pDialog=new ProgressDialog(con);
			pDialog.setMessage("Requesting for Taxi. Please wait...");
			pDialog.setIndeterminate(false);
			pDialog.setCancelable(true);
			pDialog.show();
		}

		@Override
		protected String doInBackground(String... para) {

			Calendar cal=Calendar.getInstance();
			String timedate=cal.get(Calendar.DAY_OF_MONTH)+"-"+(cal.get(Calendar.MONTH)+1)+"-"+cal.get(Calendar.YEAR)+" "+
					cal.get(Calendar.HOUR_OF_DAY)+":"+cal.get(Calendar.MINUTE)+":"+cal.get(Calendar.SECOND);

			List<NameValuePair> params=new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("driver_id", selectedDriver.getId()));
			params.add(new BasicNameValuePair("driver_email", selectedDriver.getEmail()));
			params.add(new BasicNameValuePair("driver_name", selectedDriver.getName()));
			params.add(new BasicNameValuePair("sender_id", UserInfo.getId()));
			params.add(new BasicNameValuePair("name", UserInfo.getName()));
			params.add(new BasicNameValuePair("phone", UserInfo.getPhonenumber()));
			params.add(new BasicNameValuePair("location", para[0]));
			params.add(new BasicNameValuePair("droplocation", para[1]));
			params.add(new BasicNameValuePair("latitude", gps.getLatitude()+""));
			params.add(new BasicNameValuePair("longitude", gps.getLongitude()+""));
			params.add(new BasicNameValuePair("timedate",timedate));

			JSONObject json=jparser.makeHttpRequest(taxiRequUrl, "POST", params);

			try {
				success=json.getInt("success");
				msg=json.getString("message");

			}catch (Exception e) {
				error=1;
				msg=e.getMessage();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			//Toast.makeText(MainActivity.this, s+" "+result, Toast.LENGTH_SHORT).show();
			pDialog.dismiss();
			Toast.makeText(con, msg, Toast.LENGTH_SHORT).show();

			if(error==1){
				if(Util.isConnectingToInternet(con)){
					Toast.makeText(con,"Server is down. Please try again", Toast.LENGTH_SHORT).show();
				}else{
					Util.showNoInternetDialog(con);
				}
				return;
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(handler!=null){
			handler.removeCallbacks(run);
			run=null;
			handler=null;
		}
	}
	@Override
	protected void onResume() {
		super.onResume();
		initilizeMap();
		if(handler==null){
			scheduleThread();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		alIntent= new Intent(this, UpdateReceiver.class);
		appIntent = PendingIntent.getBroadcast(this,1000, alIntent,PendingIntent.FLAG_UPDATE_CURRENT);
		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmManager.cancel(appIntent);
	}

}
