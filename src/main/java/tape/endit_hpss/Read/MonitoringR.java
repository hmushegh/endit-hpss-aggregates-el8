package tape.endit_hpss.Read;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import Application.App;
import Application.Config;
import Application.DictObject;
import Application.Utils;

// The class is used to handle different actions on the given directory. 
public class MonitoringR implements FileAlterationListener {

	private static final Logger watchReaderLog = LoggerFactory.getLogger(MonitoringR.class);
	private static final Config config = new Config();
	private static final int days = config.getDirCleanupDays(); // in days
	private static final File fileListDirFinished = config.getFileListDirFinished();
	
	 private static Path [] readRequestDir = config.getReadRequestDir();

	public void onStart(final FileAlterationObserver observer) {
		// watchReaderLog.info("The FileListener has started on "
		// + observer.getDirectory().getAbsolutePath());

		if (!observer.getDirectory().exists() || !observer.getDirectory().isDirectory()) {
			try {
				throw new RuntimeException("Directory not found: " + observer.getDirectory());
			} catch (Exception ex) {
				watchReaderLog.error("RuntimeException: ", ex);
				for (FileAlterationListener i : observer.getListeners()) {
					observer.removeListener(i);
					observer.addListener(i);
				}
			}
		}

	}

	public void onDirectoryCreate(final File directory) {
		watchReaderLog.info(directory.getAbsolutePath() + " was created.");
	}

	public void onDirectoryChange(final File directory) {
		watchReaderLog.info(directory.getAbsolutePath() + " was modified.");
	}

	public void onDirectoryDelete(final File directory) {
		watchReaderLog.info(directory.getAbsolutePath() + " was deleted.");
	}

	public void onFileCreate(final File file) {

		if (file.getAbsolutePath().contains("request")) {
			ReadAFile rObj = new ReadAFile();

			try {
				JsonObject jObj = rObj.getJsonFile(file);

				if (!jObj.isJsonNull()) {

					watchReaderLog
							.info(file.getAbsoluteFile() + " " + jObj.get("hpss_path").getAsString() + " was CREATED.");
					rObj.start(file);

				}
			} catch (IllegalArgumentException e) {
				watchReaderLog.error("IllegalArgumentException: FileName: " + "\"" + file.getName() + "\"", e);
			} catch (InterruptedException p) {
				watchReaderLog.error("InterruptedException: FileName: " + "\"" + file.getName() + "\"", p);
			} catch (IOException k) {
				watchReaderLog.error("IOException: FileName: " + "\"" + file.getName() + "\"", k);
			} catch (ExecutionException l) {
				watchReaderLog.error("ExecutionException: FileName: " + "\"" + file.getName() + "\"", l);
			} catch (NullPointerException nEx) {
				watchReaderLog.error("JSON object is null: FileName: " + "\"" + file.getName() + "\"", nEx);
			}

		}

		if (file.getAbsolutePath().contains("cancel")) {

			ReadAFile cObj = new ReadAFile();

			try {
				JsonObject jObj = cObj.getJsonFile(file);

				if (!jObj.isJsonNull()) {

					watchReaderLog
							.info(file.getAbsoluteFile() + " " + jObj.get("hpss_path").getAsString() + " was CREATED.");

					String key = jObj.get("tape_name-pos").getAsString();
					Collection<Object> values2 = Utils.syncDict.get(key);
					ArrayList<Object> valuesArrList2 = new ArrayList<>(values2);

					if (valuesArrList2.size() > 0) {
						for (int i = 0; i < valuesArrList2.size(); ++i) {
							DictObject obj = (DictObject) valuesArrList2.get(i);
							String pnfsid = obj.getFileName();

							if (pnfsid.equalsIgnoreCase(file.getName())) {
								watchReaderLog.info("Cancelled request: " + file.getAbsoluteFile()
										+ " removed from syncDict dictionary.");
								Utils.syncDict.remove(key, obj);

							}
						}
					} else {
						watchReaderLog.info("Cancelled request: " + file.getAbsoluteFile()
								+ " is no longer in syncDict and so removing it.");
					}

					// Removing a file from "cancel" directory
					String cancelDir = file.getParent().replace("request", "cancel");
					Utils.removeAFile(file.getName(), Paths.get(cancelDir));

				}
			} catch (IllegalArgumentException e) {
				watchReaderLog.error("IllegalArgumentException: FileName: " + "\"" + file.getName() + "\"", e);
			} catch (NullPointerException nEx) {
				watchReaderLog.error("JSON object is null: FileName: " + "\"" + file.getName() + "\"", nEx);
			} catch (IOException ie) {
				watchReaderLog.error("IOException: FileName: " + "\"" + file.getName() + "\"", ie);
			}
		}

	}

	public void onFileChange(final File file) {

		watchReaderLog.info(file.getAbsoluteFile() + " was MODIFIED.");

	}

	public void onFileDelete(final File file) {

		watchReaderLog.info(file.getAbsoluteFile() + " was DELETED.");
		
	}

	public void onStop(final FileAlterationObserver observer) {
		// watchReaderLog.info("The FileListener has stopped on "
		// + observer.getDirectory().getAbsolutePath());

		try {
			Utils.createAFileList(observer.getDirectory());
		} catch (InterruptedException e) {
			watchReaderLog.error("InterruptedException", e);
		}

		Utils.deleteOldFilesFromDir(fileListDirFinished, days);
		

		for (int i = 0; i < readRequestDir.length; ++i) {
			String inDir = readRequestDir[i].toString().replace("request", "in");
			Utils.deleteOldFilesFromDir(new File (inDir), days);
		}
		
		App.loggerRead.debug("Nr. objects in syncDict: " + Utils.syncDict.size() + " Nr. objects in syncTape: "
				+ Utils.syncTape.size());

	}

}
