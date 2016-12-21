package com.nas.cruzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import com.nas.cruzer.R;
import com.nas.cruzer.util.JSONParser;
import com.nas.cruzer.util.UserInfo;
import com.nas.cruzer.util.Util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class RequestedUserActivity extends Activity implements OnClickListener{
	
	public static final String dataSendUrl = "http://chakron.com/demo/cruzer/update-request.php";
	TextView nameTv,numberTv,pickupTv,dropLocTv;
	Button cancelBtn,accBtn,completeBtn;
	Context con;
	JSONParser jparser=new JSONParser();
	
	public static HashMap<String, String> request;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.requesteduser_details_layout);
		con=RequestedUserActivity.this;
		init();
		nameTv.setText("Name : "+request.get("name"));
		numberTv.setText("Phone no. : "+request.get("phone"));
		pickupTv.setText("Pickup Location : "+request.get("location").trim());
		dropLocTv.setText("Drop Location : "+request.get("droplocation").trim());
		
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	private void init() {
		nameTv=(TextView) findViewById(R.id.requestDetailsName);
		numberTv=(TextView) findViewById(R.id.requestDetailsPhone);
		pickupTv=(TextView) findViewById(R.id.requestDetailsPickup);
		dropLocTv=(TextView) findViewById(R.id.requestDetailsDrop);
		
		cancelBtn=(Button) findViewById(R.id.requestDetailsCancelBtn);
		accBtn=(Button) findViewById(R.id.requestDetailsAccBtn);
		completeBtn=(Button) findViewById(R.id.requestDetailsCompleteBtn);
		
		accBtn.setOnClickListener(this);
		cancelBtn.setOnClickListener(this);
		completeBtn.setOnClickListener(this);
		
		if(request.get("accept").equals("1")){
			accBtn.setVisibility(View.GONE);
			
		}
		if(request.get("accept").equals("2")){
			accBtn.setVisibility(View.GONE);
			cancelBtn.setVisibility(View.GONE);
			completeBtn.setVisibility(View.GONE);
		}
		if(request.get("accept").equals("3")){
			accBtn.setVisibility(View.GONE);
			cancelBtn.setVisibility(View.GONE);
			completeBtn.setVisibility(View.GONE);
		}
		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) { 
	        switch (item.getItemId()) {
	        case android.R.id.home: 
	           finish();
	            return true;
	        }

	    return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.requestDetailsCancelBtn:
			new SendData().execute("3");
			break;
		case R.id.requestDetailsAccBtn:
			new SendData().execute("1");
			break;	
		case R.id.requestDetailsCompleteBtn:
			new SendData().execute("2");
			break;	
		}
	}
	
	class SendData extends AsyncTask<String, String, String>{
		ProgressDialog pDialog;
		String s="";
		int success=-1;
		int error=0;


		boolean driver=false;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			pDialog=new ProgressDialog(con);
			pDialog.setMessage("Sending data. Please wait...");
			pDialog.setIndeterminate(false);
			pDialog.setCancelable(true);
			pDialog.show();
		}

		@Override
		protected String doInBackground(String... st) {

			List<NameValuePair> params=new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("id", request.get("id")));
			params.add(new BasicNameValuePair("accept", st[0]));

			try {
				JSONObject jobj=jparser.makeHttpRequest(dataSendUrl, "POST", params);
				success=jobj.getInt("success");
				s=jobj.getString("message");

			}catch(Exception e){
				error=1;
			}

			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			pDialog.dismiss();

			if(error==1){
				if(Util.isConnectingToInternet(con)){
					Toast.makeText(con, "Server is down, Please try again later", Toast.LENGTH_SHORT).show();
				}else
					Util.showNoInternetDialog(con);
				return;
			}

			Toast.makeText(con, s, Toast.LENGTH_LONG).show();
			if(success==0){

			}else if(success==1){
				setResult(RESULT_OK);
				finish();
			}
		}
	}

}
