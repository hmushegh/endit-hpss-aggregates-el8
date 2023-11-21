package Application;

public class HPSS {
	
	  static {
	      // load the C-library
	      try { 
	        	System.loadLibrary("EnditHpss");
	        } catch (UnsatisfiedLinkError e) { 
	            System.err.println("libEnditHpss.so library not found");
	            
	        } 
	    }
	 
	   // declaration of native method
	   public static native int login(String user, String keytabPath);
	   public static native HPSSFileInfo getFileAttr(String fileName);
	   public static native int purgeAFile(String fileName);
	   public static native int stageAFile(String fileName);
	   public static native int copyAFile(String src, String dst, String info, int buffSize, long fSize);
	   //public static synchronized native int writeAFile(String src, String dst, int cosid, int fam, String cheksum_type, String checksum_value);
	   public static native int writeAFile(String src, String dst, String pnfsid, int cosid, int fam, String cheksum_type, String checksum_value, int buffSize);	   
	   public static native int unlinkAFile(String fileName);
	   public static native void closeSysLog();

}



