package tape.endit_hpss.Write;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import Application.Config;
import Application.DictObject;
import Application.Utils;

/**
 * The class has a "start(File file)" method, that receives a metadata file.
 *  
 * The program extracts the JSON object from the metadata file, 
 * constructs the corresponding tss command and creates the tss process. 
 * 
 * If the tss process was successfully created, it writes the PID 
 * of the created tss process to the metadata file.
 * 
 * The program waits for the completion of the created tss process
 * and writes the return value of the completed process to the metadata file.
 * 
 * If the return value of the created tss process is 0, then the file
 * will be removed from the "out" directory of the ENDIT-Provider.
 *  
 * If the file no longer exists in the "out" directory of the ENDIT-Provider,
 * then the ENDIT-Provider automatically removes the corresponding metadata file
 * from its "request" directory.
 *  
 * @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
 * @year 2022
 */

public class WriteAFile {
	
		private static final Logger wLogger = LoggerFactory.getLogger(WriteAFile.class);
		private static final Config config = new Config();
		private static String vo = config.getVo();
	
		public WriteAFile () {
		
		}
		// Calls all necessary methods (extracts a JSON object from a metadata file, constructs the corresponding tss cmd and creates a tss process). 
				public void start(File file) throws InterruptedException, IOException, IllegalArgumentException, ExecutionException {
				       
			    	JsonObject jObj = getJsonFile(file);	
			    	if (!jObj.isJsonNull()) {			    					    		
			    		
			    		DictObject meta = getFileMetaData(jObj, file);
			    		
			    		if (meta.getHpssFileName() != null) {			    			
			    		
			    			addToADictionary(file, meta);
			    		
			    		}else {
			    			wLogger.error("Wrong metadata: " + meta.getHpssFileName() + "  " + meta.getChecksumType() + "  " + meta.getChecksumValue() + "  "  + " FileName: " + "\"" + file.getName() + "\"");
			    		}					
			    	}
			    	else {
			    		wLogger.error("JSON object is: " + jObj + " FileName: " + "\"" + file.getName() + "\"");
			    	}
								
				}    
				
				// Extracts a JSON object from a metadata file and returns it.
		public JsonObject getJsonFile(File file) {

					JsonObject jsonObject = null;
					try {
						String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8).trim();
						jsonObject = new JsonParser().parse(fileContent).getAsJsonObject();
						if (!jsonObject.isJsonObject()) {
							wLogger.error("Error: Could not convert to JSON object:  FileName: " + "\"" + file.getName() + "\"");
						}

					} catch (IOException e) {
						wLogger.error("IOException: FileName:" + file.getName(), e);
					} catch (IllegalStateException jEx) {
						wLogger.error("IllegalStateException: FileName: " + "\"" + file.getName() + "\"", jEx);
					} catch (JsonSyntaxException jsyntax) {
						wLogger.error("JsonSyntaxException: FileName: " + "\"" + file.getName() + "\"", jsyntax);
					}
					return jsonObject;
		}
	    
		// Extracts "hpss_path" from a given JSON object and returns it as String.
		public DictObject getFileMetaData(JsonObject jObj, File file) {

			DictObject obj = new DictObject();
			try {
				String action = jObj.get("action").getAsString();
								
				if (action.equals("migrate")) {
					String pnfsPath = jObj.get("path").getAsString();
					String checksum_type = jObj.get("checksumType").getAsString();
					String checksum_value = jObj.get("checksumValue").getAsString();
					Long filesize = jObj.get("file_size").getAsLong();
					
					if (pnfsPath != null && checksum_type != null && checksum_value != null) {	    	
													
						 switch(vo){
					        case "CMS":
					        	obj.setHpssFileName(pnfsPath.replace("/pnfs/gridka.de/cms/", "/GridKa-CMS/"));
					        	obj.setChecksumType(checksum_type);
					        	obj.setChecksumValue(checksum_value);
					        	obj.setFileSize(filesize);	
					        	obj.setFileName(file.getName());								
								obj.setReqDir(Paths.get(file.getParent()));
								String outDir = file.getParent().replace("request", "out");
								Path outDirPath = Paths.get(outDir);
								
								if (Files.notExists(outDirPath)) {
									wLogger.error("Directory: " + outDirPath + " does not exist on the dCache pool, creating it...");
									try {
										Files.createDirectory(outDirPath);
									} catch (IOException e) {
										wLogger.error ("IOException", e);
									}			
								}								
								obj.setOutDir(Paths.get(outDir));					        	
					        	break;
					        case "LHCb":					            
					            break;
					        case "Belle":
					             break;
					        case "ATLAS":
					        	break;					       
					    }
						 
						 wLogger.info("pnfspath: " + obj.getHpssFileName() + " checksumtype: " + obj.getChecksumType() + " chechsumvalue: " + obj.getChecksumValue() + " filesize: " + obj.getFileSize());
				           	
					}
				}			
				
			} catch (NullPointerException nEx) {
				wLogger.error("JSON object is: " + jObj + " FileName: " + "\"" + file.getName() + "\"", nEx);
			} catch (UnsupportedOperationException ue) {
				wLogger.error("Path is " + obj.getHpssFileName() + " FileName: " + "\"" + file.getName() + "\"", ue);
			}
			//return info;
			return obj;
		}
		
		// Adds a property ('hpss_path') to a JSON object and overwrites the metadata
		// file with a new JSON object.
		public <T> void addMeta(String fileName, String key, String value, Path dir) {
			Path requestFile = dir.resolve(fileName);
			JsonObject jsonObject = null;
			try {
				String fileContent = FileUtils.readFileToString(requestFile.toFile(), StandardCharsets.UTF_8).trim();
				jsonObject = new JsonParser().parse(fileContent).getAsJsonObject();
				if (!jsonObject.isJsonObject()) {
					wLogger.error("Failure: Could not convert to JSON object: FileName: " + "\"" + fileName + "\"");
				} else {
					if (!Objects.isNull(value)) {
						jsonObject.addProperty(key, value);
						FileUtils.writeStringToFile(requestFile.toFile(), jsonObject.toString(), StandardCharsets.UTF_8);
					} else {
						wLogger.error("Failure: Value is null: FileName: " + "\"" + fileName + "\"");
					}
				}

			} catch (IOException e) {
				wLogger.error("IOException", e);
			}

		}
		
		// Adds information about the file to the dictionary (Key: "pnfsid", Value = Object (DictObject type).
				public void addToADictionary(File file, DictObject meta)
						throws InterruptedException, IOException {
					
					Utils.syncDictW.put(file.getName(), meta);		
					
				}

		
}
