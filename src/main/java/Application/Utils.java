package Application;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import tape.endit_hpss.Read.ReadAFile;
import tape.endit_hpss.Write.WriteAFile;

import java.util.concurrent.Executors;

/**
 * The class has different methods which are called from MonitorinR class.
 * 
 * Aggregate lists are created in the given directory. From time to time the
 * program cleans the given directory from old files (for example, every 7
 * days).
 * 
 * Files are staged directly in the "in" directory of the ENDIT-Provider.
 * 
 * After successfully staging the files, the ENDIT-Provider moves these files
 * from its "in" directory to another location and removes the corresponding
 * metadata file from its "request" directory.
 * 
 * 
 * @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
 * @year 2021
 */

public class Utils {

	public static final Logger uLoggerR = LoggerFactory.getLogger(Utils.class.getName() + ".UtilsR");
	public static final Logger uLoggerW = LoggerFactory.getLogger(Utils.class.getName() + ".UtilsW");

	private static final Config config = new Config();
	private static final File fileListDirFinished = config.getFileListDirFinished();
	private static final File fileListDirActive = config.getFileListDirActive();
	private static final int listLifeTime = config.getAggregateListTime(); // in Minutes
	private static final int tapeDrives = config.getNrTapeDrives();
	private static final int retry = config.getRetry();
	private static final long retryInterv = config.getRetryInterval();

	private static final int limitNrWriteRequests = config.getNrWriteRequests();

	private static final String[] data_path = config.getDATAPaths();
	private static final String[] mc_path = config.getMCPaths();

	private static final int DATAPathsNumber = config.getDATAPathsNr();
	private static int MCPathsNumber = config.getMCPathsNr();

	private static final int DATAFFCount = config.getDATAFFCount();
	private static final int MCFFCount = config.getMCFFCount();

	private static final String vo = config.getVo();
	private static int bufSize = config.getBufferSize();

	public static SetMultimap<String, Object> readDict = HashMultimap.create();
	public static SetMultimap<String, Object> syncDict = Multimaps.synchronizedSetMultimap(readDict);

	public static SetMultimap<String, String> readTape = HashMultimap.create();
	public static SetMultimap<String, String> syncTape = Multimaps.synchronizedSetMultimap(readTape);

	public static SetMultimap<String, Object> writeDict = HashMultimap.create();
	public static SetMultimap<String, Object> syncDictW = Multimaps.synchronizedSetMultimap(writeDict);

	public static ListeningExecutorService exService = MoreExecutors
			.listeningDecorator(Executors.newSingleThreadExecutor());
	public static List<ListenableFuture> results = new ArrayList();

	public int pause = 10000; // milliseconds

	public static void createAFileList(File reqDir) throws InterruptedException {

		if (!syncDict.isEmpty()) {

			File aggregatesFileList = new File("tmp.txt");
			String keyToDelete = "";

			Calendar expireTime = Calendar.getInstance();
			expireTime.add(Calendar.MINUTE, -listLifeTime);
			Date expireDate = expireTime.getTime();

			// iterate through the key set and display key and values

			try {

				for (String key : syncDict.keySet()) {

					Collection<Object> values = syncDict.get(key);
					ArrayList<Object> valuesArrList = new ArrayList<>(values);

					DictObject lastElementObj = (DictObject) valuesArrList.get(valuesArrList.size() - 1);
					Date lastElementTime = lastElementObj.getTime();

					String[] tN = key.split("-");

					if (lastElementTime != null && lastElementTime.before(expireDate)) {

						if (syncTape.keySet().size() < tapeDrives || syncTape.containsKey(tN[0])) {

							try {

								List<String> line = new ArrayList<String>();
								for (int i = 0; i < valuesArrList.size(); ++i) {
									DictObject dObj = (DictObject) valuesArrList.get(i);
									// line.add(dObj.getFileName() + " " + dObj.getHpssFileName() + " " +
									// dObj.getInDir() + " " + Long.toString(dObj.getFileSize()));
									line.add(dObj.getFileName().trim() + " " + dObj.getHpssFileName().trim() + " "
											+ dObj.getInDir().toString().trim() + " "
											+ Long.toString(dObj.getFileSize()).trim());

									ReadAFile rObj = new ReadAFile();
									rObj.addMeta(dObj.getFileName(), "is_in_list", "yes", dObj.getReqDir());
								}

								String genFileName = String.format("%s%s%s", "aggregate_list_" + key, "_",
										System.currentTimeMillis(), "_", new Random().nextInt(1000) + ".txt");
								aggregatesFileList = new File(fileListDirActive, genFileName);

								if (aggregatesFileList.createNewFile()) {

									FileUtils.writeLines(aggregatesFileList, StandardCharsets.UTF_8.toString(), line);
									line.clear();

									uLoggerR.debug("New file created: " + aggregatesFileList.getName());

									keyToDelete = key;

									break;
								} else {
									uLoggerR.error("Could not create a new file: " + genFileName);
								}
							} catch (IOException e) {
								uLoggerR.error("IOException", e);
							}
						}
					}
				}
			} catch (ConcurrentModificationException cEx) {
				uLoggerR.error("ConcurrentModificationException", cEx);
			}

			if (!keyToDelete.isEmpty() && aggregatesFileList.length() != 0) {
				syncDict.removeAll(keyToDelete);

				String[] tName = keyToDelete.split("-");
				String tapeName = tName[0];
				String tapePosition = tName[1];

				syncTape.put(tapeName, aggregatesFileList.getName());

				final File aggrList = aggregatesFileList;

				startToRecall(aggrList, tapeName, tapePosition);

			}
		} else {
			// System.out.println("syncDict is empty");
		}

	}

	public static void startToRecall(final File aggrList, String tapeName, String tapePosition) {
		
		Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread th, Throwable ex) {
				uLoggerR.error("Uncaught exception: " + aggrList, ex);			
							
			}
		};
		Thread t = new Thread() {

			public void run() {

				try {

					recallAFileListFromHPSS(aggrList, tapeName, tapePosition);

				} catch (IOException e) {
					uLoggerR.error("IOException", e);
				} catch (InterruptedException ie) {
					uLoggerR.error("InterruptedException", ie);
				}

				// uLoggerR.error("Throw a Runtime exception");
				// throw new RuntimeException();
			}
		};
		t.setUncaughtExceptionHandler(h);
		t.start();
			
	}

	public static synchronized void updateFileStatus(final File aggregateList, String line, String status) {

		String statusUpdate;

		if (line.contains("Failed")) {
			statusUpdate = line.trim().replace("Failed", status);
		} else {
			statusUpdate = line.trim() + " " + status;
		}

		if (!line.isEmpty()) {

			// Get all the lines
			try (Stream<String> stream = Files.lines(aggregateList.toPath(), StandardCharsets.UTF_8)) {

				// Do the line replace
				List<String> list = stream.map(l -> l.equals(line) ? statusUpdate : l).collect(Collectors.toList());

				// Write the content back
				FileUtils.writeLines(aggregateList, StandardCharsets.UTF_8.toString(), list);
			}

			catch (IOException e) {
				uLoggerR.error("IOException", e);
			}
		}
	}

	public static void recallAFileListFromHPSS(final File aggregateList, String tapeName, String tapePosition)
			throws IOException, InterruptedException {

		LineIterator it = FileUtils.lineIterator(aggregateList, StandardCharsets.UTF_8.toString());
		Pattern pattern = Pattern.compile("[0-9A-F]{24,36}");

		boolean t = true;

		try {
			while (it.hasNext()) {
				String line = it.nextLine();
				Matcher matcher = pattern.matcher(line);

				if (matcher.find()) {

					//if (!line.contains("Done")) {
						String[] tmp = line.trim().split("\\s+", -1);
						String pnfsId = tmp[0].trim();
						String hpssPath = tmp[1].trim();
						String inDirStr = tmp[2].trim();

						if (tmp[3].trim().matches("[0-9]+") && tmp[3].length() > 0) {
							long fSize = Long.parseLong(tmp[3].trim());

							// Info for the log
							int sIndex = inDirStr.indexOf("f01");
							int lIndex = inDirStr.lastIndexOf("/data");
							String poolName = inDirStr.substring(sIndex, lIndex);
							String info = "Tape: " + tapeName + " RelPosition: " + tapePosition + " dCache_pool: "
									+ poolName;
							Path readInDir = Paths.get(inDirStr);
							String dstFName = readInDir + "/" + pnfsId.trim() + ".tmp";
							int count = 0;

							while (HPSS.copyAFile(hpssPath, dstFName, info, bufSize, fSize) != 0 && count < retry) {

								Thread.sleep(retryInterv);
								count++;
							}
							
							// Removes an iterated list from a dictionary
							syncTape.remove(tapeName, aggregateList.getName());

							try {

								Path dstTmp = readInDir.resolve(dstFName);

								if (Files.isRegularFile(dstTmp) && Files.size(dstTmp) == fSize) {

									// Files.setPosixFilePermissions(dstTmp,
									// PosixFilePermissions.fromString("rw-r--r--"));
									Files.move(dstTmp, dstTmp.resolveSibling(pnfsId), StandardCopyOption.ATOMIC_MOVE);

									//updateFileStatus(aggregateList, line, "Done");

									if (config.isPurgingEnabled()) {

										if (HPSS.purgeAFile(hpssPath) == 0) {
											uLoggerR.debug("The file is purged from the HPSS disk cache: " + hpssPath);
										} else
											uLoggerR.error("Cannot purge a file from the HPSS disk cache: " + hpssPath);
									}
									// Removes an iterated list from a dictionary
									//syncTape.remove(tapeName, aggregateList.getName());

								} else if (!Files.exists(dstTmp) || Files.size(dstTmp) != fSize) {
									t = false;
									Files.deleteIfExists(dstTmp); // Removing corrupted or 0 size files
									uLoggerR.debug(
											"The file: " + dstTmp + " is corrupted or has a 0 size. Removing it...");
									//updateFileStatus(aggregateList, line, "Failed");
								}

							} catch (IOException e) {
								uLoggerR.error("IOException", e);
							} catch (NumberFormatException ne) {
								uLoggerR.error(line + ne);
							}

							Thread.sleep(1500);
						} else {
							uLoggerR.error("Failed to parse it to long type: " + tmp[3].trim() + " " + aggregateList + " line: " + line);
							t = false;
						}
					//}
				}
			}

		} finally {
			it.close();
		}

		if (t) {
			uLoggerR.debug("The entire filelist was recalled successfully: FileList: " + aggregateList);
			// FileUtils.writeStringToFile(aggregateList, "Done", StandardCharsets.UTF_8,
			// true);
		} else {
			uLoggerR.debug("The entire filelist was recalled with failure(s): FileList: " + aggregateList);
			// FileUtils.writeStringToFile(aggregateList, "Done with failures",
			// StandardCharsets.UTF_8, true);

			// Removes an iterated list from a dictionary, even if all files could not be
			// recalled successfully
			// syncTape.remove(tapeName, aggregateList.getName());
		}

		FileUtils.moveFileToDirectory(aggregateList, fileListDirFinished, false);

		// Removes an iterated list from a dictionary
		// syncTape.remove(tapeName, aggregateList.getName());

		if (syncDict.isEmpty())
			HPSS.closeSysLog();

	}

	// Removes old files from directory.
	public static void deleteOldFilesFromDir(File dir, int days) {

		File[] fList = dir.listFiles();

		if (fList != null) {
			for (File file : fList) {
				if (file.isFile() && file.getName().contains("aggregate_list_") || file.getName().matches("[0-9A-F]{24,36}")) {

					long diff = new Date().getTime() - file.lastModified();
					long cutoff = (days * (24 * 60 * 60 * 1000));

					if (diff > cutoff) {
						file.delete();
						uLoggerR.debug("Expired... deleting the old file: " + file.getAbsolutePath());
					}
				}
			}
		}
	}

	public static void WriteAFileToHPSS() {

		if (!syncDictW.isEmpty()) {
			String keyToDelete = "";
			// if (results.size() < limitNrWriteRequests) {

			Utils utilObj = new Utils();
			int nrOpenProcesses = utilObj.getNrOpenProcesses();
			utilObj.limitOpenProcesses(nrOpenProcesses, limitNrWriteRequests);

			for (String pnfsid : syncDictW.keySet()) {
				Set<Object> value = syncDictW.get(pnfsid);
				DictObject dObj = (DictObject) value.iterator().next();
				WriteAFile wObj = new WriteAFile();
				wObj.addMeta(pnfsid, "hpss_path", dObj.getHpssFileName(), dObj.getReqDir());

				int FF = FAMCalculation(dObj.getHpssFileName());
				if (FF != -1) {

					dObj.setFF(FF);

					callAScript(dObj.getHpssFileName(), dObj.getOutDir().resolve(dObj.getFileName()).toString(),
							dObj.getFileName(), config.getCOSId(), dObj.getFF(), dObj.getChecksumType(),
							dObj.getChecksumValue());

					keyToDelete = pnfsid;

				} else
					uLoggerW.error("FAM calculation fails, file: " + "\"" + dObj.getHpssFileName() + "\"");
				break;
			}
			syncDictW.removeAll(keyToDelete);
		}

		// }
		else
			HPSS.closeSysLog();

	}

	public static void callAScript(String src, String dst, String pnfsid, int cosid, int fam, String cheksum_type,
			String checksum_value) {

		try {

			String[] str = new String[] { "/bin/sh", config.getWriteBashScript(), src, dst, pnfsid,
					Integer.toString(cosid), Integer.toString(fam), cheksum_type, checksum_value };

			Process pScript = Runtime.getRuntime().exec(str);

			pScript.getErrorStream().close();
			pScript.getInputStream().close();
			pScript.getOutputStream().close();

		} catch (IOException e) {
			uLoggerW.error("IOException: FileName:" + src + "  " + dst, e);
		}

	}

	public int getNrOpenProcesses() {

		String command = "pgrep C_HPSS_Write";
		int count = 0;

		try {
			Process pr = Runtime.getRuntime().exec(command);
			pr.getErrorStream().close();
			pr.getOutputStream().close();

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(pr.getInputStream()));

			while ((stdInput.readLine()) != null) {
				count++;
			}

			return count - 1;

		} catch (Exception ex) {
			uLoggerW.error("Exception: " + ex + "  cmd: " + command);

			return count - 1;
		}
	}

	public int limitOpenProcesses(int nrOpenProc, int limit) {

		nrOpenProc++;

		while (nrOpenProc >= limit) {

			try {

				uLoggerW.debug("Limit of active requests:" + limit + " is reached. Pause 'file writing' process for "
						+ pause / 1000 + " seconds.");

				Thread.sleep(pause);
				nrOpenProc = getNrOpenProcesses();

			} catch (InterruptedException e) {

				uLoggerW.error("Interrupted Exception: " + e);

			}

		}

		return nrOpenProc;

	}

	public static int FAMCalculation(String hpssPath) {

		int fam = -1;
		int count = 0;

		Pattern pattern = Pattern.compile(config.getFAMPattern());
		Matcher matcher = pattern.matcher(hpssPath);

		String datasetName = "";
		String utf8EncodedString = "";

		if (matcher.matches()) {
			datasetName = matcher.group(1);
			ByteBuffer buffer = StandardCharsets.UTF_8.encode(datasetName);
			utf8EncodedString = StandardCharsets.UTF_8.decode(buffer).toString();
		} else {
			uLoggerW.error("Patther does not match file: " + "\"" + hpssPath + "\"");
			return -1;
			// return 52; // for the test
		}

		String toHex = DigestUtils.sha1Hex(utf8EncodedString);

		if (data_path.length != 0) {
			for (int i = 0; i < data_path.length; i++) {
				if (hpssPath.contains(data_path[i])) {
					int ff = Integer.parseInt(toHex.substring(0, 2), 16) % DATAFFCount;
					switch (vo) {
					case "CMS":
						fam = ff + DATAPathsNumber;
						break;
					case "LHCb":
						fam = ff + DATAPathsNumber + 3 * i;
						break;
					case "Belle":
						fam = ff + DATAPathsNumber;
						break;
					case "ATLAS":
						break;
					}
					count++;
					break;
				}
			}
		} else {
			uLoggerW.error("'DATA.path' attribute is empty in the config file.");
		}

		if (mc_path.length != 0) {
			if (count == 0) {
				for (int i = 0; i < mc_path.length; i++) {
					if (hpssPath.contains(mc_path[i])) {
						int ff = Integer.parseInt(toHex.substring(0, 2), 16) % MCFFCount;
						fam = ff + MCPathsNumber;
					}
				}
			}
		} else {
			uLoggerW.error("'MC.path' attribute is empty in the config file.");
		}

		return fam;
	}

	public static int HPSSVerify(String fName) {

		String verify_cmd = "/opt/hpss/bin/hpsssum -k -t " + config.getKeyTabFile() + " -p " + config.getUser() + " -f "
				+ fName + " -u verify -b 5242880";

		int exitValue = -1;

		try {

			Process p = Runtime.getRuntime().exec(verify_cmd);

			if (p.isAlive()) {
				p.getErrorStream().close();
				p.getInputStream().close();
				p.getOutputStream().close();

				uLoggerW.info("hpsssum process opened, Verify cmd: " + verify_cmd);

				try {
					exitValue = p.waitFor();

				} catch (InterruptedException e) {
					uLoggerW.error("InterruptedException, file: " + "\"" + fName + "\"", e);
				}
			} else {
				uLoggerW.error("hpsssum process opening failed, file: " + "\"" + fName + "\"");
			}

		} catch (IOException e) {
			uLoggerW.error("IOException, file: " + "\"" + fName + "\"");
		}

		return exitValue;
	}

	// Removes a file from the "out" directory
	public static boolean removeAFile(String fileName, Path dir) throws IOException {

		if (Files.deleteIfExists(dir.resolve(fileName))) {
			return true;
		}
		return false;
	}

}
