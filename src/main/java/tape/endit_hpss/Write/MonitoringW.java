package tape.endit_hpss.Write;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import Application.App;
import Application.Utils;
import Application.Config;


// The class is used to handle different actions on the given directory. 
public class MonitoringW implements FileAlterationListener {
	private static final Logger watchWriterLog = LoggerFactory.getLogger(MonitoringW.class);
	
	Utils utilObj = new Utils();
	Config config = new Config();
	//int nrOpenProcesses = utilObj.getNrOpenProcesses();
	 
    public void onStart(final FileAlterationObserver observer) {
		//watchWriterLog.info("The FileListener has started on "
		//      + observer.getDirectory().getAbsolutePath() + "\n");    	

    	if (!observer.getDirectory().exists() || !observer.getDirectory().isDirectory()) {						
					try {   
						throw new RuntimeException("Directory not found: " + observer.getDirectory());
					} catch (Exception ex) {						
						watchWriterLog.error("RuntimeException: ", ex);
						for (FileAlterationListener i : observer.getListeners()) {				
							  observer.removeListener(i);	
							  observer.addListener(i);	
						  }
					}
		}
    }
 
     
    public void onDirectoryCreate(final File directory) {
    	watchWriterLog.info(directory.getAbsolutePath() + " was created.");
    }
 
     
    public void onDirectoryChange(final File directory) {   
    	watchWriterLog.info(directory.getAbsolutePath() + " was modified.");
    }
 
     
    public void onDirectoryDelete(final File directory) {        
    	watchWriterLog.info(directory.getAbsolutePath() + " was deleted.");
    }
 
     
    public void onFileCreate(final File file) {  
    	
	if (file.getAbsolutePath().contains("request")) {
		
		 WriteAFile wFile = new WriteAFile();
   		 JsonObject jObj = wFile.getJsonFile(file); 
   
   	try {
   		
   	    if (!jObj.isJsonNull() && !jObj.has("hpss_path")){
   	    	  	    		 
   	    	//nrOpenProcesses = utilObj.limitOpenProcesses(nrOpenProcesses, config.getNrWriteRequests());
		    watchWriterLog.info(file.getAbsoluteFile() + " was CREATED."); 
   			wFile.start(file);			
   		}    	    
   	} catch (IllegalArgumentException e) {
				watchWriterLog.error("IllegalArgumentException: FileName: " + "\"" + file.getName() + "\"",e);			
   		 }catch (InterruptedException p){
   			watchWriterLog.error("InterruptedException: FileName: " + "\"" + file.getName() + "\"",p);
   		 }catch (IOException k) {
   			watchWriterLog.error("IOException: FileName: " + "\"" + file.getName() + "\"",k);
   		 }catch (ExecutionException l) {
   			watchWriterLog.error("ExecutionException: FileName: "  + "\"" + file.getName() + "\"",l);
   		 }catch (NullPointerException nEx) {
   			watchWriterLog.error("JSON object is null: FileName: " + "\"" + file.getName() + "\"",nEx);
   		 }
   	   	 
	}
	
    }
 
     
    public void onFileChange(final File file) {
    	    	 
    	watchWriterLog.info(file.getAbsoluteFile() + " was MODIFIED."); 
    }
 
     
    public void onFileDelete(final File file) {
     	
   		watchWriterLog.info(file.getAbsoluteFile() + " was DELETED."); 
     
    }
 
     
    public void onStop(final FileAlterationObserver observer) {
    	//watchWriterLog.info("The FileListener has stopped on "
    	//		+ observer.getDirectory().getAbsolutePath() + "\n"); 
         
    	Utils.WriteAFileToHPSS();
    	
    	App.loggerWrite.debug( "Nr. objects in 'syncDictW': " + Utils.syncDictW.size());
    	/*try {
    		
    	   App.loggerWrite.debug( "Nr. objects in 'results': " + Utils.results.size());    		
		   Utils.checkHPSSVerifyExitValues();			
		  
			
		} catch (InterruptedException e) {
			watchWriterLog.error("InterruptedException: ",e);
		} catch (ExecutionException p) {
			watchWriterLog.error("ExecutionException: ",p);
		}*/
    	
    	
    }   
     
}

