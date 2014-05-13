/******************************************************************************
 * Project:  Metro Access
 * Purpose:  Routing in subway for disabled.
 * Author:   Baryshnikov Dmitriy (aka Bishop), polimax@mail.ru
 ******************************************************************************
*   Copyright (C) 2014 NextGIS
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ****************************************************************************/
package com.nextgis.metroaccess.data;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

import com.nextgis.metroaccess.MainActivity;
import com.nextgis.metroaccess.R;

import edu.asu.emit.qyan.alg.control.YenTopKShortestPathsAlg;
import edu.asu.emit.qyan.alg.model.Graph;
import edu.asu.emit.qyan.alg.model.Path;
import edu.asu.emit.qyan.alg.model.VariableGraph;
import edu.asu.emit.qyan.alg.model.Vertex;

public class MAGraph {
	
	protected Graph m_oGraph;
	protected YenTopKShortestPathsAlg m_oYenAlg;
	
	protected boolean m_bDirected;
	protected File m_oExternalDir;
	
	protected Map<String, GraphDataItem> m_moRouteMetadata;
	protected List<GraphDataItem> m_asChoiceItems;
	//protected static String msRDataPath;
	
	protected Map<Integer, StationItem> m_moStations;
	protected Map<String, int[]> m_moCrosses;
	protected Map<Integer, String> m_omLines;
	
	protected String m_sCurrentCity;
	protected boolean m_bIsValid;
	protected String m_sErr;
	
	protected String m_sLocale;
	protected Context m_oContext;
	protected String m_sFirstCity;
	
	public MAGraph(Context oContext, String sCurrentCity, File oExternalDir, String sLocale) {
		
		this.m_oContext = oContext;
		
		this.m_bDirected = false;
		this.m_oExternalDir = oExternalDir;
		this.m_sLocale = sLocale;
		
		m_moRouteMetadata = new HashMap<String, GraphDataItem>();
		m_asChoiceItems = new ArrayList<GraphDataItem>();
		m_moStations = new HashMap<Integer, StationItem>();
		m_moCrosses = new HashMap<String, int[]>();
		m_omLines = new HashMap<Integer, String>();
		
		FillRouteMetadata();
		
		m_oGraph = new VariableGraph();
		
		SetCurrentCity(sCurrentCity);
	}
	
	public boolean IsValid(){
		return m_bIsValid;
	}
	
	public String GetLastError(){
		return m_sErr;
	}
	
	protected boolean LoadIntercharges(){
		m_moCrosses.clear();	
		//fill interchanges.csv
		try {
    		File oRouteDataDir = new File(GetCurrentRouteDataPath());
    		File intercharges_file = new File(oRouteDataDir, "interchanges.csv");		    

			if (intercharges_file.exists()) {
				InputStream in = new BufferedInputStream(new FileInputStream(intercharges_file));
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String line = reader.readLine();
		        while ((line = reader.readLine()) != null) {
		             String[] RowData = line.split(MainActivity.CSV_CHAR);
		             
		             //station_from;station_to;max_width;min_step;min_step_ramp;lift;lift_minus_step;min_rail_width;max_rail_width;max_angle
		             
		             if(RowData.length != 10){
		     	    	 m_sErr = m_oContext.getString(R.string.sInvalidCSVData) + "interchanges.csv";
		            	 return false;
		             }
		             
					 int nFromId = Integer.parseInt(RowData[0]);
					 int nToId = Integer.parseInt(RowData[1]);
					 int[] naBarriers = {0,0,0,0,0,0,0,0};
					 for(int i = 2; i < 10; i++){
						 int nVal = Integer.parseInt(RowData[i]);
						 naBarriers[i - 2] = nVal;
					 }	 
					 m_moCrosses.put("" + nFromId + "->" + nToId, naBarriers);					 
		        }
		        reader.close();
		        if (in != null) {
		        	in.close();
		    	}
			}
			else{
				m_sErr = m_oContext.getString(R.string.sCannotGetPath);
				return false;
			}
			    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		}	
    	return true;
	}
	
	protected boolean LoadGraph(){
		//fill routes
    	try {
    		File oRouteDataDir = new File(GetCurrentRouteDataPath());
    		File file_route = new File(oRouteDataDir, "graph.csv");
			if (file_route.exists()) {
	        	InputStream in;
				in = new BufferedInputStream(new FileInputStream(file_route));
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		        String line = reader.readLine();
		        while ((line = reader.readLine()) != null) {
		             String[] RowData = line.split(MainActivity.CSV_CHAR);
		             
		             if(RowData.length != 5){
		     	    	 m_sErr = m_oContext.getString(R.string.sInvalidCSVData) + "graph.csv";
		            	 return false;
		             }
		             
					 int nFromId = Integer.parseInt(RowData[0]);
					 int nToId = Integer.parseInt(RowData[1]);
					 int nCost = Integer.parseInt(RowData[4]);
	 					 
					 //Log.d("Route", ">" + nFromId + "-" + nToId + ":" + nCost);
					 m_oGraph.add_edge(nFromId, nToId, nCost);
					 if(!m_bDirected){
						 m_oGraph.add_edge(nToId, nFromId, nCost);
					 }
		        }
		        reader.close();
		        if (in != null) {
		        	in.close();
		    	}
			}
			else{
				m_sErr = m_oContext.getString(R.string.sCannotGetPath);
				return false;
			}
			    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		}	
		m_oYenAlg = new YenTopKShortestPathsAlg(m_oGraph);
		
		return true;			
	}
	
	protected boolean LoadPortals(){
		String sFileName = "portals_" + m_sLocale + ".csv";	
    	try {
    		File oRouteDataDir = new File(GetCurrentRouteDataPath());
    		File portals_file = new File(oRouteDataDir, sFileName);
		    if(!portals_file.exists())
		    	portals_file = new File(oRouteDataDir, "portals_en.csv");

			if (portals_file.exists()) {
			   	InputStream in;
				in = new BufferedInputStream(new FileInputStream(portals_file));
	
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	
		        String line = reader.readLine();
		        while ((line = reader.readLine()) != null) {
		             String[] RowData = line.split(MainActivity.CSV_CHAR);
		             
		             if(RowData.length < 4){
		     	    	 m_sErr = m_oContext.getString(R.string.sInvalidCSVData) + "portals.csv";
		            	 return false;
		             }
		             
					 int nID = Integer.parseInt(RowData[0]);
					 String sName = RowData[1];
					 int nStationId = Integer.parseInt(RowData[2]);
					 int nDirection = 0;
					 if(RowData[3].equals("in")){
						 nDirection = 1;
					 }
					 else if(RowData[3].equals("out")){
						 nDirection = 2;
					 }
					 else{
					 	nDirection = 3;
					 }						
					 
					 int min_width = 0;
					 int min_step = 0;
					 int min_step_ramp = 0;
					 int lift = 0;
					 int lift_minus_step = 0;
					 int min_rail_width = 0;
					 int max_rail_width = 0;
					 int max_angle = 0;
					 
					 if(RowData.length > 13)
					 {
						 String tmp = RowData[6];
						 min_width = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[7];
						 min_step = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[8];
						 min_step_ramp = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[9];
						 lift = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[10];
						 lift_minus_step = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[11];
						 min_rail_width = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[12];
						 max_rail_width = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
						 tmp = RowData[13];
						 max_angle = tmp.length() == 0 ? 0 : Integer.parseInt(tmp);
					 }
					 int [] detailes = {min_width, min_step, min_step_ramp, lift, lift_minus_step, min_rail_width, max_rail_width, max_angle};
					 PortalItem pt = new PortalItem(nID, sName, nStationId, nDirection, detailes);
	
					 StationItem item = m_moStations.get(nStationId);
					 if(item == null){
						 m_sErr = "Station #" + nStationId + " is underfined.";
						 Log.d(MainActivity.TAG, m_sErr);
						 return false;
					 }
					 m_moStations.get(nStationId).AddPortal(pt);
		        }
		        
		        reader.close();
			    if (in != null) {
			       	in.close();
			   	} 
			}
			else{
				m_sErr = m_oContext.getString(R.string.sCannotGetPath);
				return false;
			}
			    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		}	
    	return true;
	}
	
	protected boolean LoadStations(){
		m_moStations.clear();
		String sFileName = "stations_" + m_sLocale + ".csv";	
    	try {
    		File oRouteDataDir = new File(GetCurrentRouteDataPath());
    		File station_file = new File(oRouteDataDir, sFileName);
		    if(!station_file.exists())
	    		station_file = new File(oRouteDataDir, "stations_en.csv");
		    
			if (station_file.exists()) {
	        	InputStream in;
				in = new BufferedInputStream(new FileInputStream(station_file));
	       	
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	
		        String line = reader.readLine();
		        while ((line = reader.readLine()) != null) {
		             String[] RowData = line.split(MainActivity.CSV_CHAR);
		             
		             if(RowData.length < 4){
		            	 m_sErr = m_oContext.getString(R.string.sInvalidCSVData) + "stations.csv";
		            	 return false;
		             }
		             
					 String sName = RowData[3];
					 int nNode = Integer.parseInt(RowData[2]);
					 int nLine = Integer.parseInt(RowData[1]);
					 int nID = Integer.parseInt(RowData[0]);
	 					 
					 m_oGraph.add_vertex(new Vertex(nID));
					 StationItem st = new StationItem(nID, sName, nLine, nNode, 0);
	 				     
				     m_moStations.put(nID, st);
		        }
			        
		        reader.close();
		        if (in != null) {
		        	in.close();
		    	} 
			}
			else{
				m_sErr = m_oContext.getString(R.string.sCannotGetPath);
				return false;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		}
    	
    	return true;
	}
	
	protected boolean LoadLines(){
		m_omLines.clear();
    	try {        	
		    //fill with lines list
    		String sFileName = "lines_" + m_sLocale + ".csv";	
    		File oRouteDataDir = new File(GetCurrentRouteDataPath());
    		File lines_file = new File(oRouteDataDir, sFileName);
		    if(!lines_file.exists())
		    	lines_file = new File(oRouteDataDir, "lines_en.csv");
		        		
			if (lines_file.exists()) {
	        	InputStream in;
				in = new BufferedInputStream(new FileInputStream(lines_file));
	       	
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	
		        String line = reader.readLine();
		        while ((line = reader.readLine()) != null) {
		             String[] RowData = line.split(MainActivity.CSV_CHAR);
		             
					 String sName = RowData[1];
					 int nLineId = Integer.parseInt(RowData[0]);
					 
					 m_omLines.put(nLineId, sName);
		        }
			        
		        reader.close();
		        if (in != null) {
		        	in.close();
		    	} 
			}
			else{
				m_sErr = m_oContext.getString(R.string.sCannotGetPath);
				return false;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			m_sErr = e.getLocalizedMessage();
			return false;
		}
    	
    	return true;
	}
	
	public void FillRouteMetadata(){
		m_moRouteMetadata.clear();
		m_sFirstCity = "";
		//fill meta
		File oRouteDataDir = new File(m_oExternalDir, MainActivity.GetRouteDataDir());
		if(!oRouteDataDir.exists())
			return;
		
		File[] files = oRouteDataDir.listFiles();
		for (File inFile : files) {
		    if (inFile.isDirectory()) {
		        File metafile = new File(inFile, MainActivity.GetMetaFileName());
		        if(metafile.isFile()){
		        	String sJSON = MainActivity.readFromFile(metafile);
		        	JSONObject oJSON;
					try {
						oJSON = new JSONObject(sJSON);
						String sLocaleKeyName = "name_" + Locale.getDefault().getLanguage();
						String sName = oJSON.getString("name");
						String sLocName = oJSON.getString(sLocaleKeyName);	
						if(sLocName.length() == 0)
							sLocName = sName;
						int nVer = oJSON.getInt("ver");	
						boolean bDirected = oJSON.getBoolean("directed");
			        	
			        	GraphDataItem Item = new GraphDataItem(nVer, sName, sLocName, inFile.getName(), 0, bDirected, m_oContext.getString(R.string.sKB), m_oContext.getString(R.string.sMB));
			        	
			        	m_moRouteMetadata.put(inFile.getName(), Item);	
			        	
			        	if(m_sFirstCity.length() < 2){
			        		m_sFirstCity = inFile.getName();
			        	}
			        	
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		        }
		    }
		}		
	}
	
	public String GetLocale(){
		return m_sLocale;
	}
	
	public void SetLocale(String sLocale){
		if(m_sLocale.equals(sLocale))
			return;
			
		m_sLocale = sLocale;
		
		if(!LoadStations())
			return;
		if(!LoadLines())
			return;
		
	}
	
	public String GetCurrentCity(){
		return m_sCurrentCity;
	}

	public void SetCurrentCity(String sCurrentCity){
		if(m_sCurrentCity != null && m_sCurrentCity.equals(sCurrentCity))
			return;
		
		m_sCurrentCity = sCurrentCity;
		
		m_bIsValid = false;
		
		//load data for current city
		if(!LoadStations())
			return;
		if(!LoadLines())
			return;
		if(!LoadPortals())
			return;
		if(!LoadIntercharges())
			return;
		if(!LoadGraph())
			return;
		
		m_bIsValid = true;
	}
	
	public boolean IsRoutingDataExist(){			
		return !m_moRouteMetadata.isEmpty();
	}
	
	public void SetFirstCityAsCurrent(){
		if(IsRoutingDataExist()){
			SetCurrentCity(m_sFirstCity);
		}
	}
	
	public boolean HasStations(){
		return m_moStations != null && !m_moStations.isEmpty();
	}
	
	public StationItem GetStation(int nStationId){
		return m_moStations.get(nStationId);
	}
	
	public void OnUpdateMeta(String sJSONData, boolean bOnlyNewer){
		try{
			JSONObject oJSONMetaRemote = new JSONObject(sJSONData);
			
			//save remote meta to file
			if(oJSONMetaRemote != null ){
				File file = new File(m_oExternalDir, MainActivity.GetRemoteMetaFile());
				MainActivity.writeToFile(file, sJSONData);
			}			
			
			final JSONArray jsonArray = oJSONMetaRemote.getJSONArray("packages");

			m_asChoiceItems.clear();

			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String sLocaleKeyName = "name_" + Locale.getDefault().getLanguage();
				String sName = jsonObject.getString("name");
				String sLocName = jsonObject.getString(sLocaleKeyName);	
				if(sLocName.length() == 0)
					sLocName = sName;
				int nVer = jsonObject.getInt("ver");
				boolean bDirected = jsonObject.getBoolean("directed");
				int nSize = 0;
				if(jsonObject.has("size")){
					nSize = jsonObject.getInt("size");
				}
				String sPath = jsonObject.getString("path");
						
				GraphDataItem Item = new GraphDataItem(nVer, sName, sLocName, sPath, nSize, bDirected, m_oContext.getString(R.string.sKB), m_oContext.getString(R.string.sMB));
				if(bOnlyNewer){
					for (Map.Entry<String, GraphDataItem> entry : m_moRouteMetadata.entrySet()) {
						if(Item.IsNewer(entry.getValue())){
							m_asChoiceItems.add(Item);
							break;
						}					
					}
				}
				else{
					m_asChoiceItems.add(Item);
				}
			}	
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public List<GraphDataItem> HasChanges(){
		//ask user for download
		return m_asChoiceItems;
	}
		
	public List<Path> GetShortestPaths(int nDepartureStationId, int nArrivalStationId, int nMaxRouteCount){
		return m_oYenAlg.get_shortest_paths(m_oGraph.get_vertex(nDepartureStationId), m_oGraph.get_vertex(nArrivalStationId), nMaxRouteCount);
	}
	
	public boolean IsEmpty(){
		return m_moRouteMetadata.isEmpty();
	}
	
	public Map<String, GraphDataItem> GetRouteMetadata(){
		return m_moRouteMetadata;
	}
	
	public String GetCurrentRouteDataPath(){
		File oRouteDataDir = new File(m_oExternalDir, MainActivity.GetRouteDataDir());
		File oCurrentRouteDataDir = new File(oRouteDataDir, GetCurrentCity());
		return oCurrentRouteDataDir.getAbsolutePath();
	}
	
	public Map<Integer, String> GetLines() {
		return m_omLines;
	}
	
	public Map<Integer, StationItem> GetStations(){
		return m_moStations;
	}
	
	public Map<String, int[]> GetCrosses(){
		return m_moCrosses;
	}
}
