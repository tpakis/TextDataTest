package com.bxl.textdatatest;

import java.util.ArrayList;
import java.util.Set;

import com.bxl.BXLConst;
import com.bxl.config.editor.BXLConfigLoader;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import jpos.JposConst;
import jpos.JposException;
import jpos.POSPrinter;
import jpos.POSPrinterConst;
import jpos.config.JposEntry;
import jpos.events.ErrorEvent;
import jpos.events.ErrorListener;
import jpos.events.OutputCompleteEvent;
import jpos.events.OutputCompleteListener;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;

public class MainActivity extends Activity
implements OnItemClickListener, OnClickListener, StatusUpdateListener, OutputCompleteListener, ErrorListener {

	private static final int REQUEST_CODE_BLUETOOTH = 1;

	private static final String DEVICE_ADDRESS_START = " (";
	private static final String DEVICE_ADDRESS_END = ")";

	private EditText dataEditText;
	private Spinner escapeSequencesSpinner;

	private final ArrayList<CharSequence> bondedDevices = new ArrayList<>();
	private ArrayAdapter<CharSequence> arrayAdapter;

	private BXLConfigLoader bxlConfigLoader;
	private POSPrinter posPrinter;
	private String logicalName;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setBondedDevices();

		arrayAdapter = new ArrayAdapter<>(this,
				android.R.layout.simple_list_item_single_choice, bondedDevices);
		ListView listView = (ListView) findViewById(R.id.listViewPairedDevices);
		listView.setAdapter(arrayAdapter);

		listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		listView.setOnItemClickListener(this);

		dataEditText = (EditText) findViewById(R.id.editTextData);
		dataEditText.setSelection(dataEditText.getText().length());
		dataEditText.setText("ЁАБВГДЕЖЗИЙКЛМНОПУФХЦЧШЩЪЫЬЭЮЯб\nвгдежзийклмнопрстуфхцчшщъыьэюяё");

		escapeSequencesSpinner = (Spinner) findViewById(R.id.spinnerEscapeSequences);

		findViewById(R.id.buttonAdd).setOnClickListener(this);
		findViewById(R.id.buttonPrint).setOnClickListener(this);

		bxlConfigLoader = new BXLConfigLoader(this);
		try {
			bxlConfigLoader.openFile();
		} catch (Exception e) {
			e.printStackTrace();
			bxlConfigLoader.newFile();
		}
		posPrinter = new POSPrinter(this);
		
		posPrinter.addErrorListener(this);
		posPrinter.addOutputCompleteListener(this);
		posPrinter.addStatusUpdateListener(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		try {
			posPrinter.close();
		} catch (JposException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
			startActivityForResult(intent, REQUEST_CODE_BLUETOOTH);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_BLUETOOTH) {
			setBondedDevices();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		String device = ((TextView) view).getText().toString();

		String name = device.substring(0, device.indexOf(DEVICE_ADDRESS_START));

		String address = device.substring(device.indexOf(DEVICE_ADDRESS_START)
				+ DEVICE_ADDRESS_START.length(),
				device.indexOf(DEVICE_ADDRESS_END));

		try {
			for (Object entry : bxlConfigLoader.getEntries()) {
				JposEntry jposEntry = (JposEntry) entry;
				bxlConfigLoader.removeEntry(jposEntry.getLogicalName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			logicalName = setProductName(name);
			bxlConfigLoader.addEntry(logicalName,
					BXLConfigLoader.DEVICE_CATEGORY_POS_PRINTER, 
					logicalName,
					BXLConfigLoader.DEVICE_BUS_BLUETOOTH, address);
			
			bxlConfigLoader.saveFile();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private String setProductName(String name){
		String productName = BXLConfigLoader.PRODUCT_NAME_SPP_R200II;
		
		if((name.indexOf("SPP-R200II")>=0)){
			if(name.length() > 10){
				if(name.substring(10, 11).equals("I")){
					productName = BXLConfigLoader.PRODUCT_NAME_SPP_R200III;
				}
			}
		}else if((name.indexOf("SPP-R210")>=0)){
			productName = BXLConfigLoader.PRODUCT_NAME_SPP_R210;
		}else if((name.indexOf("SPP-R310")>=0)){
			productName = BXLConfigLoader.PRODUCT_NAME_SPP_R310;
		}else if((name.indexOf("SPP-R300")>=0)){
			productName = BXLConfigLoader.PRODUCT_NAME_SPP_R300;
		}else if((name.indexOf("SPP-R400")>=0)){
			productName = BXLConfigLoader.PRODUCT_NAME_SPP_R400;
		}
			
		return productName;
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.buttonAdd:

			String text = EscapeSequence.getString(escapeSequencesSpinner.getSelectedItemPosition());
			dataEditText.getText().insert(dataEditText.getSelectionStart(), text);
			break;

		case R.id.buttonPrint:

			print();
			break;
		}
	}

	private void setBondedDevices() {
		logicalName = null;
		bondedDevices.clear();

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		Set<BluetoothDevice> bondedDeviceSet = bluetoothAdapter
				.getBondedDevices();

		for (BluetoothDevice device : bondedDeviceSet) {
			bondedDevices.add(device.getName() + DEVICE_ADDRESS_START
					+ device.getAddress() + DEVICE_ADDRESS_END);
		}

		if (arrayAdapter != null) {
			arrayAdapter.notifyDataSetChanged();
		}
	}

	private void print() {

		String data = dataEditText.getText().toString();

		try {
			posPrinter.open(logicalName);
			posPrinter.claim(0);
			posPrinter.setDeviceEnabled(true);
			
			posPrinter.setCharacterEncoding(BXLConst.CS_928_GREEK);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, data + "\n");
			/*
			String ESC = new String(new byte[]{0x1b, 0x7c});
			String LF = "\n";
			
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT,  ESC + "!uC" + ESC + "cA" + ESC + "4C" + ESC + "bC" + "TICKETsrv Presentation"+ LF);
			posPrinter.printBarCode(POSPrinterConst.PTR_S_RECEIPT, "263036991401;4tk", POSPrinterConst.PTR_BCS_QRCODE, 8, 8, POSPrinterConst.PTR_BC_CENTER, POSPrinterConst.PTR_BC_TEXT_BELOW);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "!uC" + ESC + "cA" + ESC + "bC" + "Comp: TICKETsrv" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "!uC" + ESC + "cA" + ESC + "bC" + "Walton, KT12 3BS" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "!uC" + ESC + "cA" + ESC + "bC" + "Tel: 01932 901 155" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "!uC" + ESC + "cA" + ESC + "bC" + "123-456-789" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "!uC" + ESC + "cA" + ESC + "bC" +  "VAT No. 123456789" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "cA"  + ESC + "bC" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "lA" + ESC + "bC"  + "Sale:       " + "19-05-2017 16:19:43"+ LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, "Gate:       " + "Xcover kiosk" + LF );
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, "Operator:   " + "Rob" + LF );
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, "Order Code: " + "263036991" + LF + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "uC" + "Qty  Price    Item                    Total     " + ESC + "!uC" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, "1    £8.00    PARKING                 £8.00" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, LF); 
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "rA" +     "Total (inc VAT):  "+ "     £8.00" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "rA" + "VAT amount (20%): "+ "     £1.33" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "rA" + "CARD payment:     "+ "     £8.00" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "rA" + "Change due:       "+ "     £0.00" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "cA" + ESC + "bC" + "Thank you for your purchase!" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "cA" + ESC + "bC" + "Enjoy the show!" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "cA" + ESC + "bC" + "Next year visit" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "cA" + ESC + "bC" + "development.ticketsrv.co.uk" + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "cA" + ESC + "bC" + "to buy discounted tickets." + LF);
			posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESC + "N" + ESC + "bC" + ESC + "cA" + " " + LF + LF  );
			*/
		} catch (JposException e) {
			e.printStackTrace();
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
		} 
		finally {
			try {
				posPrinter.close();
			} catch (JposException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void outputCompleteOccurred(final OutputCompleteEvent e) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				Toast.makeText(MainActivity.this, "complete print", Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void errorOccurred(final ErrorEvent arg0) {
		// TODO Auto-generated method stub
		runOnUiThread(new Runnable() {

			@Override
			public void run() {

				Toast.makeText(MainActivity.this, "Error status : " + getERMessage(arg0.getErrorCodeExtended()), Toast.LENGTH_SHORT).show();

				if(getERMessage(arg0.getErrorCodeExtended()).equals("Power off")){
					try
					{
						posPrinter.close();
					}
					catch(JposException e)
					{
						e.printStackTrace();
						Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
					}
					// port-close
				}else if(getERMessage(arg0.getErrorCodeExtended()).equals("Cover open")){
					//re-print
				}else if(getERMessage(arg0.getErrorCodeExtended()).equals("Paper empty")){
					//re-print
				}


			}
		});
	}

	@Override
	public void statusUpdateOccurred(final StatusUpdateEvent arg0) {
		// TODO Auto-generated method stub
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				Toast.makeText(MainActivity.this, "printer status : " + getSUEMessage(arg0.getStatus()), Toast.LENGTH_SHORT).show();

				if(getSUEMessage(arg0.getStatus()).equals("Power off")){
					Toast.makeText(MainActivity.this, "check the printer - Power off", Toast.LENGTH_SHORT).show();
				}else if(getSUEMessage(arg0.getStatus()).equals("Cover Open")){
					//display message
					Toast.makeText(MainActivity.this, "check the printer - Cover Open", Toast.LENGTH_SHORT).show();
				}else if(getSUEMessage(arg0.getStatus()).equals("Cover OK")){
					//re-print
				}else if(getSUEMessage(arg0.getStatus()).equals("Receipt Paper Empty")){
					//display message
					Toast.makeText(MainActivity.this, "check the printer - Receipt Paper Empty", Toast.LENGTH_SHORT).show();
				}else if(getSUEMessage(arg0.getStatus()).equals("Receipt Paper OK")){
					//re-print
				}
			}
		});
	}

	private static String getERMessage(int status){
		switch(status){
		case POSPrinterConst.JPOS_EPTR_COVER_OPEN:
			return "Cover open";

		case POSPrinterConst.JPOS_EPTR_REC_EMPTY:
			return "Paper empty";

		case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
			return "Power off";

		default:
			return "Unknown";
		}
	}


	private static String getSUEMessage(int status){
		switch(status){
		case JposConst.JPOS_SUE_POWER_ONLINE:
			return "Power on";

		case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
			return "Power off";

		case POSPrinterConst.PTR_SUE_COVER_OPEN:
			return "Cover Open";

		case POSPrinterConst.PTR_SUE_COVER_OK:
			return "Cover OK";

		case POSPrinterConst.PTR_SUE_REC_EMPTY:
			return "Receipt Paper Empty";

		case POSPrinterConst.PTR_SUE_REC_NEAREMPTY:
			return "Receipt Paper Near Empty";

		case POSPrinterConst.PTR_SUE_REC_PAPEROK:
			return "Receipt Paper OK";

		case POSPrinterConst.PTR_SUE_IDLE:
			return "Printer Idle";

		default:
			return "Unknown";
		}
	}
}


