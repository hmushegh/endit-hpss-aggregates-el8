package tape.endit_hpss.Read;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
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
import Application.HPSS;
import Application.HPSSFileInfo;
import Application.Utils;

/**
 * The class has a "start(File file)" method, that receives a metadata file. It
 * extracts a JSON object from the metadata file, gets the file attribute
 * information from hpss and puts it as an object (Value= Object (DictObject))
 * in a dictionary with a "tape+position" key.
 * 
 * After the list of aggregates is made, another dictionary holds an information
 * about them. Key="tape" and Value = "AggrgateList name"
 * 
 * @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
 * @year 2021
 */

public class ReadAFile {

	private static final Logger rLogger = LoggerFactory.getLogger(ReadAFile.class);

	private static final Config config = new Config();
	private static Path[] readRequestDir = config.getReadRequestDir();

	public ReadAFile() {

	}

	// The starting point
	// Extracts a JSON object from a metadata file
	// Gets file attributes from HPSS
	// Adds the necessary information to the dictionary
	public void start(File file)
			throws InterruptedException, IOException, IllegalArgumentException, ExecutionException {

		if (config.isP2PMoveEnabled()) {

			boolean fileExistOnPools = P2PMove(file);

			if (!fileExistOnPools) {
				rLogger.debug(
						"The file " + file.getName() + " doesn't exist on any dcache pool, so copying it from HPSS");
				prepareFileForCopyingFromHPSS(file);
			} else {
				rLogger.debug("The file " + file.getName()
						+ " exists on another dcache pool, so moving it from another pool to 'in' and skipping the copy procedure from HPSS");
			}
		} else {
			prepareFileForCopyingFromHPSS(file);
		}

	}

	public void prepareFileForCopyingFromHPSS(File file)
			throws InterruptedException, IOException, IllegalArgumentException, ExecutionException {

		JsonObject jObj = getJsonFile(file);
		if (!jObj.isJsonNull()) {
			String hpssFile = getHpssFile(jObj, file);
			rLogger.debug("hpss_path:" + hpssFile + " FileName: " + "\"" + file.getName() + "\"");
			if (!hpssFile.trim().isEmpty()) {
				HPSSFileInfo hpssFileAttr = HPSS.getFileAttr(hpssFile);
				if (hpssFileAttr.getTapeName() != null) {
					addToADictionary(file, hpssFile, hpssFileAttr);
				}
			} else {
				rLogger.debug("JSON object is: " + jObj + " FileName: " + "\"" + file.getName() + "\"");
			}
		}

	}

	public boolean P2PMove(File file) {

		for (int i = 0; i < readRequestDir.length; ++i) {

			Path dataPool = readRequestDir[i].getParent();
			File src = dataPool.resolve(file.getName()).toFile();

			try {
				if (src.exists() && Files.isRegularFile(src.toPath()) && (Files.size(src.toPath()) != 0)) {

					String inDir = file.getParent().replace("request", "in");
					Path inDirPath = Paths.get(inDir);

					try {
						FileUtils.moveFileToDirectory(src, inDirPath.toFile(), false);

						return true;
					}

					catch (Exception e) {
						rLogger.error("Exception", e);
					}
				}

			} catch (IOException ex) {
				rLogger.error("IOException", ex);
			}

		}

		return false;

	}

	// Extracts a JSON object from a metadata file and returns it.
	public JsonObject getJsonFile(File file) {

		JsonObject jsonObject = null;
		try {
			String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8).trim();
			jsonObject = new JsonParser().parse(fileContent).getAsJsonObject();
			if (!jsonObject.isJsonObject()) {
				rLogger.error("Error: Could not convert to JSON object:  FileName: " + "\"" + file.getName() + "\"");
			}

		} catch (IOException e) {
			rLogger.error("IOException: FileName:" + file.getName(), e);
		} catch (IllegalStateException jEx) {
			rLogger.error("IllegalStateException: FileName: " + "\"" + file.getName() + "\"", jEx);
		} catch (JsonSyntaxException jsyntax) {
			rLogger.error("JsonSyntaxException: FileName: " + "\"" + file.getName() + "\"", jsyntax);
		}
		return jsonObject;
	}

	// Extracts "hpss_path" from a given JSON object and returns it as String.
	public String getHpssFile(JsonObject jObj, File file) {

		String hpssFile = "";
		try {
			String action = jObj.get("action").getAsString();
			if (action.equals("recall")) {
				hpssFile = jObj.get("hpss_path").getAsString();
			}

		} catch (NullPointerException nEx) {
			rLogger.error("JSON object is: " + jObj + " FileName: " + "\"" + file.getName() + "\"", nEx);
		} catch (UnsupportedOperationException ue) {
			rLogger.error("Path is " + hpssFile + " FileName: " + "\"" + file.getName() + "\"", ue);
		}
		return hpssFile;

	}

	// Adds a property ('is_in_list') to a JSON object and overwrites the metadata
	// file with a new JSON object.
	public <T> boolean addMeta(String fileName, String key, String value, Path dir) {
		Path requestFile = dir.resolve(fileName);
		JsonObject jsonObject = null;
		try {
			String fileContent = FileUtils.readFileToString(requestFile.toFile(), StandardCharsets.UTF_8).trim();
			jsonObject = new JsonParser().parse(fileContent).getAsJsonObject();
			if (!jsonObject.isJsonObject()) {
				rLogger.error("Failure: Could not convert to JSON object: FileName: " + "\"" + fileName + "\"");
				return false;
			} else {
				if (!Objects.isNull(value)) {
					jsonObject.addProperty(key, value);
					FileUtils.writeStringToFile(requestFile.toFile(), jsonObject.toString(), StandardCharsets.UTF_8);
				} else {
					rLogger.error("Failure: Value is null: FileName: " + "\"" + fileName + "\"");
					return false;
				}
			}

		} catch (IOException e) {
			rLogger.error("IOException", e);
			return false;
		} catch (IllegalStateException il) {
			rLogger.error("IllegalStateException", il);
			return false;
		}
		return true;

	}

	// Adds information about the file to the dictionary (Key: "tape+relposition", Value = Object (DictObject type).
	public void addToADictionary(File file, String hpssFile, HPSSFileInfo fInfo)
			throws InterruptedException, IOException {

		DictObject dObj = new DictObject();
		dObj.setHpssFileName(hpssFile);
		dObj.setFileName(file.getName());
		dObj.setFileSize(fInfo.getFilesize());
		dObj.setTime(Calendar.getInstance().getTime());

		dObj.setReqDir(Paths.get(file.getParent()));
		String inDir = file.getParent().replace("request", "in");
		Path inDirPath = Paths.get(inDir);

		if (Files.notExists(inDirPath)) {
			rLogger.error("Directory: " + inDirPath + " does not exist on the dCache pool, creating it...");
			Files.createDirectory(inDirPath);
		}

		dObj.setInDir(Paths.get(inDir));

		String tapeRelPos = fInfo.getTapeName() + "-" + Integer.toString(fInfo.getRelPosition());

		if (Utils.syncDict.put(tapeRelPos.trim(), dObj)) {

			if (!addMeta(file.getName(), "tape_name-pos", tapeRelPos.trim(), Paths.get(file.getParent()))) {
				rLogger.error("Failure: Could not add a property ('tapeName-pos') to a JSON object: FileName: " + "\""
						+ file.getName() + "\"");
			}
		}

	}

}
