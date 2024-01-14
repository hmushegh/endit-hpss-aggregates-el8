package Application;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import tape.endit_hpss.Read.MonitoringR;
import tape.endit_hpss.Read.ReadAFile;
import tape.endit_hpss.Write.WriteAFile;
import tape.endit_hpss.Write.MonitoringW;
import java.nio.file.Paths;
import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 *  The main class that initiates the entire procedure of reading files from tape.
 *   
 * @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
 * @year 2021
 */


public class App {
	
	public static final Logger loggerRead = LoggerFactory.getLogger(App.class.getName()+".AppRead");
	public static final Logger loggerWrite = LoggerFactory.getLogger(App.class.getName()+".AppWrite");
	    
    private static final long pollingInterval = 5 * 1000; //The monitor will perform polling on the folder every 5 seconds
    
    private static final Config config = new Config();	 
    
    private static Path [] readRequestDir;    
    private static Path [] writeRequestDir;
    
	private static final File fileListDirActive = config.getFileListDirActive();
    
    private static String user = config.getUser();
    private static String keyTabFile = config.getKeyTabFile();
    
        
    public static void FileReading() throws Exception  {	    	
		
    	   	
    	ResumeActiveLists(fileListDirActive.toPath());

    	readRequestDir = config.getReadRequestDir();
    	    	
    	loggerRead.debug("Properties file loaded with parameters:" + " Make list time: " +  config.getAggregateListTime() + " Tape drives: " + config.getNrTapeDrives() 
    	+ " Directory cleanup days: " + config.getDirCleanupDays() + " Retry: " + config.getRetry() + " Retry interval: " + config.getRetryInterval());    	    	 	    	
     	
     	int rc = HPSS.login(user, keyTabFile);
     	
     	if (rc == 0) { 
     		
     		for (int i = 0; i < readRequestDir.length; ++i) {
    			try {
    				MonitorMultiPoolsR(readRequestDir[i]);
    			} catch (Exception e) {
    				loggerRead.error("Exception: Look at MonitorMultiPools() ", e);
    			}
    		}
  	       
     	}else {
     		loggerRead.error("HPSS login fails");
     	}
    }
    
    
    public static void ResumeActiveLists(Path dir) throws Exception {
    	
    	 final File activeDir = new File(dir.toString()); 
  	 
    	// Lists the directory if it is not empty
  		 File[] fileList = activeDir.listFiles();		  
  	       if (fileList.length != 0 ) {  	
  	      	   
  	    	   loggerRead.info("Resuming all active aggregated lists, in total: " + fileList.length + " lists. Done!"); 
  	      	
  	           for (File file: fileList) { 
       	 	  	
	        	 Pattern pattern = Pattern.compile("[A-Z][^_]*");       
	             Matcher matcher = pattern.matcher(file.getName());
	              
	             if(matcher.find()) {        	      	
	            	String key = matcher.group(0); 
	            	String[] tName = key.split("-");
	 				String tapeName = tName[0];
	 				String tapePosition = tName[1];
	 				
	 				if (Utils.syncTape.put(tapeName, file.getName())) {
	 					Utils.startToRecall(file, tapeName, tapePosition);	 				
	 				}
	           }
  	        	   	        		
  	       }  	        	       
			//log the dictionary
			
			  try {	    
			    	Set<String> keys = Utils.syncTape.keySet();
			    	
			    	for (String tape : keys) {
			    		loggerRead.info(tape + ":" + Utils.syncTape.get(tape));	
			    		
			     }
			    }	catch (ConcurrentModificationException cEx){
			    	loggerRead.error("ConcurrentModificationException: " , cEx);	    	
				 		
				 }
     }
  	  else {
  		loggerRead.info("There are no active aggregated lists, in total: " + fileList.length + "lists. Done!"); 
  	   }
    }    
    
 public static void MonitorMultiPoolsR(Path dir) throws Exception {
	 
	// Specifies the directory that needed to be watched/monitored.    	    	 
	 final File directory = new File(dir.toString()); 
	  	   	
      // Lists the directory if it is not empty
		 File[] fileList = directory.listFiles();		  
	       if (fileList.length != 0) {
	    	   
	    	// Switches on monitoring of new files
	    	   switchOnFileMonitoring(directory, "Read");
	    	  
	           for (File file: fileList) {  
	        	   if (dir.toString().contains("request")) {
	        		   if (file.isFile() && file.length()>0) {
	        			   ReadAFile rObj = new ReadAFile();    			      			   
	        			    try {
	        				   JsonObject jObj = rObj.getJsonFile(file);  
	        				   
	        				   if (!jObj.has("is_in_list")){
	   	            			try {
	   	            				rObj.start(file);    
	   	            				loggerRead.info(file.getAbsoluteFile() + " " + jObj.get("hpss_path").getAsString() + " was picked up.");
	   	            				
	   	     	           	  	}catch (IllegalArgumentException e) {
	   	     	           	  	loggerRead.error("IllegalArgumentException: FileName: " + "\"" + file.getName() + "\"", e);
	   	     	           	  	}
	     	            		  } else {
	     	            			loggerRead.info("'is_in_list' record already exists in the metadata file, resuming it from existing active lists..." + "\"" + jObj.get("hpss_path").getAsString() + "\"");	            	    	
	   	            		}         				  	        				   
            			
	        			   }catch (NullPointerException nEx) {
	        				   loggerRead.error("JSON object is null: FileName:" + "\"" + file.getName() + "\"", nEx);
	        			   } 	       
	               }	               
	              }else if (dir.toString().contains("cancel")) {
	            	  Files.deleteIfExists(dir.resolve(file.getName()));
	              }
	       }//
	           
	            } else {
	            	 	// Switches on monitoring of new files
	            	switchOnFileMonitoring(directory, "Read");
	       }
	 
 }
  
 public static void MonitorMultiPoolsW(Path dir) throws Exception {
	 
	 Utils utilObj = new Utils();
 	 int nrOpenProcesses = utilObj.getNrOpenProcesses();
	 
		// Specifies the directory that needed to be watched/monitored.
     final File directory = new File(dir.toString());         
                	       
     // Lists the directory if it is not empty        
		 File[] fileList = directory.listFiles();		  
	       if (fileList.length != 0) {
	    	   
	    	   // Switches on monitoring of new files
	    	   switchOnFileMonitoring(directory, "Write");  	   
		    	   
	    	   for (File file: fileList) {
	               if (file.isFile() && file.length()>0) {
	            	   WriteAFile wFile = new WriteAFile();
	            	   try {
	            		   JsonObject jObj = wFile.getJsonFile(file);   
	            		     if (jObj.has("exitValue") && jObj.get("exitValue").getAsInt() == 0){ 
	            			   loggerWrite.info("File exists in HPSS, skipping writing it again... FileName: " + "\"" + file.getName() + "\"");
		            				// Calls the removeFile method
	            			             String outDir = file.getParent().replace("request", "out"); 
		            					if (Utils.removeAFile(file.getName(), Paths.get(outDir))) { 			
		            						loggerWrite.info("FileName: " + "\"" + file.getName() + "\"" + " removed from the 'out' directory ");
		            					}else {
		            						loggerWrite.info("Cannot delete the file from 'out' directory: " + "\"" + file.getName() + "\"");
		            					}
		            		 }	

     	       		   if (!jObj.has("hpss_path")){
	            			try {
	            				// nrOpenProcesses = utilObj.limitOpenProcesses(nrOpenProcesses, config.getNrWriteRequests());
	            				 wFile.start(file); 	
	            				 loggerWrite.info(file.getAbsoluteFile() + " was picked up.");  
	            				 
	            				 
	     	           	  }catch (IllegalArgumentException e) {
	     	           		loggerWrite.error("IllegalArgumentException: FileName: " + "\"" + file.getName() + "\"", e);
	     	           	  }
	            		}else {
	            			loggerWrite.info("'hpss_path' record already exists in the meta-data file, skipping file migration..." + "\"" + file.getName() + "\"");
	            		} 
     	       } catch (NullPointerException nEx) {
	           			loggerWrite.error("JSON object is null: FileName: " + "\"" + file.getAbsolutePath() + "\"", nEx);
	            	}
	              }
	           	}
	           }else {
	        	   // Switches on monitoring of new files
	        	   switchOnFileMonitoring(directory, "Write");
	           }
	 
 }
    
 public static void FileWriting() throws Exception  {
    	
    	writeRequestDir = config.getWriteRequestDir();
    	
    	loggerWrite.debug("Properties file loaded with parameters:" + " VO: " + config.getVo() + " CosID: " + config.getCOSId() + " FAM pattern: " + config.getFAMPattern()
    	+ " limit write requests: " + config.getNrWriteRequests());   
           	   	
    	int rc = HPSS.login(user, keyTabFile);
    	    	
    	if (rc == 0) { 
     		
     		for (int i = 0; i < writeRequestDir.length; ++i) {
    			try {
    				MonitorMultiPoolsW(writeRequestDir[i]);
    				    			     
    			} catch (Exception e) {
    				loggerWrite.error("Exception: Look at MonitorMultiPools() ", e);
    			}
    		}
  	       
     	}else {
     		loggerWrite.error("HPSS login fails");
     	}    	
    	   	
     	
    }

    //
    public static void switchOnFileMonitoring (File directory, String action) throws Exception {
    	
    	 // Create a new FileAlterationObserver on the given directory
        FileAlterationObserver fileObserver = new FileAlterationObserver(directory);
        
        if (directory.getAbsolutePath().contains("sT")){         	
        	fileObserver.addListener(new MonitoringR());
        }else {         	
        	fileObserver.addListener(new MonitoringW());      	
        }
 
        // Create a new FileAlterationMonitor with the given pollingInterval period
        final FileAlterationMonitor monitor = new FileAlterationMonitor(
                pollingInterval);
 
        // Add the previously created FileAlterationObserver to FileAlterationMonitor
        monitor.addObserver(fileObserver);
 
        // Start the FileAlterationMonitor
        monitor.start();
        
        if (action.equalsIgnoreCase("Read"))        
        	loggerRead.info("Start monitoring of '" + directory + "' folder" );
        else if (action.equalsIgnoreCase("Write"))
        	loggerWrite.info("Start monitoring of '" + directory + "' folder" );
        else {
        	loggerRead.info("No action specified" );
        	loggerWrite.info("No action specified" );
        } 	
    }
    
    
    // Main method
	public static void main(String[] args) throws Exception  {	
	    			
		String str = args[0];
			
		if (str.equalsIgnoreCase("read")) {			
			System.out.println("File reading starts...");			
			FileReading();		
		} else if (str.equalsIgnoreCase("write")) {
			System.out.println("File writing starts...");
			FileWriting();
			
		}		
		else {			
			System.out.println("No argument or wrong argument passed..!");		
		}
				
	}

}
