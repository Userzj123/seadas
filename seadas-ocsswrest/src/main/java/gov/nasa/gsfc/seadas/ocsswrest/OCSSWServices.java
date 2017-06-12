package gov.nasa.gsfc.seadas.ocsswrest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nasa.gsfc.seadas.ocsswrest.database.SQLiteJDBC;
import gov.nasa.gsfc.seadas.ocsswrest.ocsswmodel.OCSSWRemote;
import gov.nasa.gsfc.seadas.ocsswrest.ocsswmodel.OCSSWServerModel;
import gov.nasa.gsfc.seadas.ocsswrest.process.ProcessRunner;
import gov.nasa.gsfc.seadas.ocsswrest.utilities.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by IntelliJ IDEA.
 * User: Aynur Abdurazik (aabduraz)
 * Date: 1/8/15
 * Time: 12:17 PM
 * To change this template use File | Settings | File Templates.
 */

@Path("/ocssw")
public class OCSSWServices {

    private static final String GET_OBPG_FILE_TYPE_PROGRAM_NAME = "get_obpg_file_type.py";
    private static String NEXT_LEVEL_NAME_FINDER_PROGRAM_NAME = "next_level_name.py";
    private static String NEXT_LEVEL_FILE_NAME_TOKEN = "Output Name:";
    private static String FILE_TABLE_NAME = "FILE_TABLE";
    private static String MISSION_TABLE_NAME = "MISSION_TABLE";

    private HashMap<String, Boolean> missionDataStatus;

    @GET
    @Path("/installDir")
    @Produces(MediaType.TEXT_PLAIN)
    public String getOCSSWInstallDir() {
        System.out.println("ocssw install dir: " + OCSSWServerModelOld.OCSSW_INSTALL_DIR);
        return OCSSWServerModelOld.OCSSW_INSTALL_DIR;
    }

    @GET
    @Path("/ocsswInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getOcsswInfo() {
        JsonObject ocsswInstallStatus = Json.createObjectBuilder().add("ocsswExists", OCSSWServerModel.isOCSSWExist())
                .add("ocsswRoot", OCSSWServerModel.getOcsswRoot())
                .build();
        return ocsswInstallStatus;
    }

    /**
     * This method uploads client id and saves it in the file table. It also decides the working directory for the client and saves it in the table for later requests.
     *
     * @param jobId    jobId is specific to each request from the a SeaDAS client
     * @param clientId clientId identifies one SeaDAS client
     * @return
     */
    @PUT
    @Path("/ocsswSetClientId/{jobId}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response setClientId(@PathParam("jobId") String jobId, String clientId) {
        Response.Status respStatus = Response.Status.OK;
        System.out.println("client : " + clientId );
        SQLiteJDBC.updateItem(FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.CLIENT_ID_NAME.getFieldName(), clientId);
        String workingDirPath = System.getProperty("user.home") + File.separator + clientId;
        System.out.println("client and working directory: " + clientId + "   " + workingDirPath);
        SQLiteJDBC.updateItem(FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.WORKING_DIR_PATH.getFieldName(), workingDirPath);
        return Response.status(respStatus).build();
    }

    @PUT
    @Path("/ocsswSetProgramName/{jobId}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response setOCSSWProgramName(@PathParam("jobId") String jobId, String programName) {
        Response.Status respStatus = Response.Status.OK;
        if (OCSSWServerModel.isProgramValid(programName)) {
            SQLiteJDBC.updateItem(FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.PROGRAM_NAME.getFieldName(), programName);
        } else {
            respStatus = Response.Status.BAD_REQUEST;

        }
        return Response.status(respStatus).build();
    }

    @GET
    @Path("/getOfileName/{jobId}")
    @Consumes(MediaType.TEXT_XML)
    public JsonObject getOfileName(@PathParam("jobId") String jobId) {

        String missionName = SQLiteJDBC.retrieveItem(SQLiteJDBC.FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.MISSION_NAME.getFieldName());
        String fileType = SQLiteJDBC.retrieveItem(SQLiteJDBC.FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.I_FILE_TYPE.getFieldName());
        String programName = SQLiteJDBC.retrieveItem(SQLiteJDBC.FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.PROGRAM_NAME.getFieldName());
        String ofileName = SQLiteJDBC.retrieveItem(SQLiteJDBC.FILE_TABLE_NAME, jobId, SQLiteJDBC.FileTableFields.O_FILE_NAME.getFieldName());
        ofileName = ofileName.substring(ofileName.lastIndexOf(File.separator) + 1);
        JsonObject fileInfo = Json.createObjectBuilder().add("missionName", missionName)
                .add("fileType", fileType)
                .add("programName", programName)
                .add("ofileName", ofileName)
                .build();
        return fileInfo;
    }

    @PUT
    @Path("executeOcsswProgram/{jobId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeOcsswProgram(@PathParam("jobId") String jobId, JsonObject jsonObject)
            throws IOException {
        Response.Status respStatus = Response.Status.OK;
        Process process = null;
        if (jsonObject == null) {
            respStatus = Response.Status.BAD_REQUEST;
        } else {

            OCSSWRemote ocsswRemote = new OCSSWRemote();
            process = ocsswRemote.executeProgram(jobId, jsonObject);
        }
        if (process != null) {
            System.out.println("process execution completed.");
            System.out.print("exit code = ");
            try {
                int exitValue = process.waitFor();
                System.out.println(exitValue);
                SQLiteJDBC.updateItem("PROCESSOR_TABLE", jobId, "EXIT_VALUE", new Integer(exitValue).toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Response.status(respStatus).build();
    }

    @GET
    @Path("missions")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getMissionDataStatus() {
        return new MissionInfo().getMissions();
    }

    @GET
    @Path("retrieveMissionDirName")
    @Produces(MediaType.TEXT_PLAIN)
    public String getMissionSuitesArray() {
        return OCSSWServerModelOld.missionDataDir;
    }

    @GET
    @Path("/l2bin_suites/{missionName}")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getMissionSuites(@PathParam("missionName") String missionName) {
        return new MissionInfo().getL2BinSuites(missionName);
    }


    @GET
    @Path("downloadInstaller")
    @Produces(MediaType.TEXT_XML)
    public boolean getOCSSWInstallerDownloadStatus() {
        return OCSSWServerModelOld.downloadOCSSWInstaller();
    }


    @GET
    @Path("/evalDirInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getOCSSWEvalDirInfo() {
        JsonObject evalDirStatus = Json.createObjectBuilder().add("eval", new File(OCSSWServerModelOld.missionDataDir + "eval").exists()).build();
        return evalDirStatus;
    }

    @GET
    @Path("/srcDirInfo")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public JsonObject getOCSSWSrcDirInfo() {
        JsonObject srcDirStatus = Json.createObjectBuilder().add("build", new File(OCSSWServerModelOld.missionDataDir + "build").exists()).build();
        return srcDirStatus;
    }

    @GET
    @Path("/ocsswEnv")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public OCSSWInfo getOCSSWInfo() {
        OCSSWInfo ocsswInfo = new OCSSWInfo();
        //ocsswInfo.setInstalled(true);
        //ocsswInfo.setOcsswDir(System.getProperty("user.home") + "/ocssw");
        return ocsswInfo;
    }

    @GET
    @Path("/serverSharedFileDir")
    @Produces(MediaType.TEXT_PLAIN)
    public String getSharedFileDirName() {
        System.out.println("Shared dir name:" + OCSSWServerPropertyValues.getServerSharedDirName());
        return OCSSWServerPropertyValues.getServerSharedDirName();
    }

    @POST
    @Path("/updateProgressMonitorFlag/{progressMonitorFlag}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void updateProgressMonitorFlag(@PathParam("progressMonitorFlag") String progressMonitorFlag) {
        System.out.println("Shared dir name:" + OCSSWServerPropertyValues.getServerSharedDirName());
        OCSSWServerModelOld.setProgressMonitorFlag(progressMonitorFlag);
    }

    @POST
    @Path("/findIFileTypeAndMissionName/{jobId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public String findFileTypeAndMissionName(@PathParam("jobId") String jobId, JsonArray jsonArray) {
        Response.Status respStatus = Response.Status.OK;
        Process process = null;
        String missionName = null;
        String fileType = null;

        if (jsonArray == null) {
            respStatus = Response.Status.INTERNAL_SERVER_ERROR;
        } else {
            System.out.println("finding file type and mission name  ");

            String[] cmdArray = getCmdArray(jsonArray);

            cmdArray[0] = OCSSWServerModelOld.getOcsswScriptPath();
            cmdArray[1] = "--ocsswroot";
            cmdArray[2] = OCSSWServerModelOld.getOcsswEnv();

            for (String str : cmdArray) {
                System.out.println(str);
            }

            process = ProcessRunner.executeCmdArray(cmdArray);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            try {
                String line = stdInput.readLine();
                System.out.println("Line = " + line);
                if (line != null) {
                    String splitLine[] = line.split(":");
                    if (splitLine.length == 3) {
                        missionName = splitLine[1].toString().trim();
                        fileType = splitLine[2].toString().trim();
                        System.out.println("Mission Name = " + missionName);
                        System.out.println("File Type = " + fileType);
                    }
                }
            } catch (IOException ioe) {
                System.out.println(ioe.getStackTrace());
            }
        }


        if (jobId != null) {
            //insert or update mission name
            if (SQLiteJDBC.isJobIdExist(jobId)) {
                SQLiteJDBC.updateMissionName(jobId, missionName);
            } else {
                SQLiteJDBC.insertMissionName(jobId, missionName);
            }

            //insert or update file type
            if (SQLiteJDBC.isJobIdExist(jobId)) {
                SQLiteJDBC.updateFileType(jobId, fileType);

            } else {
                SQLiteJDBC.insertFileType(jobId, fileType);
            }


        }
        int exitValue = process.exitValue();
        SQLiteJDBC.updateItem("PROCESSOR_TABLE", OCSSWServerModelOld.getCurrentJobId(), "EXIT_VALUE", new Integer(exitValue).toString());
        return new Integer(exitValue).toString();
    }

    @GET
    @Path("/lonlat2pixel")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject lonlat2pixelConverter(JsonArray jsonArray) {
        Process process = null;
        String programName = "lonlat2pixel";
        HashMap<String, String> pixels = new HashMap<>();
        String[] cmdArray = getCmdArray(jsonArray);
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        cmdArray[0] = OCSSWServerModelOld.getOcsswScriptPath();
        cmdArray[1] = "--ocsswroot";
        cmdArray[2] = OCSSWServerModelOld.getOcsswEnv();
        process = ProcessRunner.executeCmdArray(cmdArray);
        String jsonString = new String();
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0) {
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = br.readLine();
                String[] tmp;
                while ((line = br.readLine()) != null) {
                    if (jsonString.length() > 0) {
                        jsonString = jsonString + ",";
                    }
                    if (line.indexOf("=") != -1) {
                        tmp = line.split("=");
                        pixels.put(tmp[0], tmp[1]);
                        jsonString = jsonString + (jsonString.length() > 0 ? "," : "") + tmp[0] + " : " + tmp[1];
                        jsonObjectBuilder.add(tmp[0], tmp[1]);
                    }
                }
            }

        } catch (IOException ioe) {

        } catch (InterruptedException ie) {

        }
        JsonObject jo = jsonObjectBuilder.build();
        return jo;
    }

    @POST
    @Path("install")
    @Consumes(MediaType.APPLICATION_JSON)
    public void installOcssw() {

    }

    @POST
    @Path("cmdArray")
    @Consumes(MediaType.APPLICATION_JSON)
    public String uploadCommandArray(JsonArray jsonArray)
            throws IOException {
        Response.Status respStatus = Response.Status.OK;
        Process process = null;
        if (jsonArray == null) {
            respStatus = Response.Status.INTERNAL_SERVER_ERROR;
        } else {
            writeToFile(jsonArray.getString(0));
            downloadOCSSWInstaller();

            String[] cmdArray = getCmdArray(jsonArray);

            cmdArray[0] = OCSSWServerModelOld.OCSSW_INSTALLER_FILE_LOCATION;

            process = ProcessRunner.executeInstaller(cmdArray);
        }
        int exitValue = process.exitValue();
        SQLiteJDBC.updateItem("PROCESSOR_TABLE", OCSSWServerModelOld.getCurrentJobId(), "EXIT_VALUE", new Integer(exitValue).toString());
        return new Integer(exitValue).toString();
    }

    @POST
    @Path("installOcssw")
    @Consumes(MediaType.APPLICATION_JSON)
    public String installOcssw(JsonArray jsonArray)
            throws IOException {
        Response.Status respStatus = Response.Status.OK;
        Process process = null;
        if (jsonArray == null) {
            respStatus = Response.Status.INTERNAL_SERVER_ERROR;
        } else {
            writeToFile(jsonArray.getString(0));
            System.out.println("download installer: ");
            System.out.println(downloadOCSSWInstaller());

            String[] cmdArray = getCmdArray(jsonArray);

            cmdArray[0] = OCSSWServerModelOld.OCSSW_INSTALLER_FILE_LOCATION;

            for (String str : cmdArray) {
                System.out.println(str);
            }

            process = ProcessRunner.executeInstaller(cmdArray);
        }
        int exitValue = process.exitValue();
        SQLiteJDBC.updateItem("PROCESSOR_TABLE", OCSSWServerModelOld.getCurrentJobId(), "EXIT_VALUE", new Integer(exitValue).toString());
        return new Integer(exitValue).toString(); //Response.status(respStatus).build();
    }

    @POST
    @Path("runOcsswProcessor")
    @Consumes(MediaType.APPLICATION_JSON)
    public String runOcsswProcessor(JsonArray jsonArray)
            throws IOException {
        Response.Status respStatus = Response.Status.OK;
        Process process = null;
        if (jsonArray == null) {
            respStatus = Response.Status.INTERNAL_SERVER_ERROR;
        } else {

            String[] cmdArray = getCmdArray(jsonArray);

            cmdArray[0] = OCSSWServerModelOld.getOcsswScriptPath();
            cmdArray[1] = "--ocsswroot";
            cmdArray[2] = OCSSWServerModelOld.getOcsswEnv();

            process = ProcessRunner.executeInstaller(cmdArray);
        }
        int exitValue = process.exitValue();
        SQLiteJDBC.updateItem("PROCESSOR_TABLE", OCSSWServerModelOld.getCurrentJobId(), "EXIT_VALUE", new Integer(exitValue).toString());
        return new Integer(exitValue).toString();//Response.status(respStatus).build();
    }



    @GET
    @Path("retrieveNextLevelFileName/{jobId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String findNextLevelFileName(@PathParam("jobId") String jobId) {
        return SQLiteJDBC.retrieveItem(FILE_TABLE_NAME, jobId, "O_FILE_NAME");
    }

    @GET
    @Path("retrieveIFileType/{jobId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String findIFileType(@PathParam("jobId") String jobId) {
        return SQLiteJDBC.retrieveItem(FILE_TABLE_NAME, jobId, "I_FILE_TYPE");
    }

    @GET
    @Path("retrieveMissionName/{jobId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String findMissionName(@PathParam("jobId") String jobId) {
        return SQLiteJDBC.retrieveItem(FILE_TABLE_NAME, jobId, "MISSION_NAME");
    }


    @GET
    @Path("retrieveMissionDirName/{jobId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String findMissionDirName(@PathParam("jobId") String jobId) {
        return SQLiteJDBC.retrieveItem(FILE_TABLE_NAME, jobId, "MISSION_DIR");
    }


    @GET
    @Path("retrieveProcess/{jobId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Process retrieveProcess(@PathParam("jobId") String jobId) {
        ObjectMapper om = new ObjectMapper();
        try {
            String processString = om.writeValueAsString(OCSSWServerModelOld.getProcess(jobId));
        } catch (JsonProcessingException jpe) {
            System.out.println(jpe.getStackTrace());
        }
        return OCSSWServerModelOld.getProcess(jobId);
    }

    @GET
    @Path("retrieveProcessResult/{jobId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream retrieveProcessResult(@PathParam("jobId") String jobId) {
        return OCSSWServerModelOld.getProcess(jobId).getInputStream();
    }


    private static String[] getCmdArrayForNextLevelNameFinder(String ifileName, String programName) {
        String[] cmdArray = new String[6];
        cmdArray[0] = OCSSWServerModelOld.getOcsswScriptPath();
        cmdArray[1] = "--ocsswroot";
        cmdArray[2] = OCSSWServerModelOld.getOcsswEnv();
        cmdArray[3] = NEXT_LEVEL_NAME_FINDER_PROGRAM_NAME;
        cmdArray[4] = ifileName;
        cmdArray[5] = programName;
        return cmdArray;

    }

    private String[] getCmdArray(JsonArray jsonArray) {
        String text = "cmdArray: ";
        String str;
        ArrayList<String> list = new ArrayList<String>();
        if (jsonArray != null) {
            int len = jsonArray.size();
            for (int i = 0; i < len; i++) {
                str = jsonArray.get(i).toString();
                str = str.replace('"', ' ');
                str = str.trim();
                list.add(str);
                text = text + str;
            }
        }
        writeToFile(text);

        String[] cmdArray = list.toArray(new String[list.size()]);
        return cmdArray;
    }

    public static boolean downloadOCSSWInstaller() {

        try {

            URL website = new URL(OCSSWServerModelOld.OCSSW_INSTALLER_URL);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(OCSSWServerModelOld.OCSSW_INSTALLER_FILE_LOCATION);
            fos.getChannel().transferFrom(rbc, 0, 1 << 24);
            fos.close();
            (new File(OCSSWServerModelOld.OCSSW_INSTALLER_FILE_LOCATION)).setExecutable(true);
            ocsswInstalScriptDownloadSuccessful = true;
        } catch (MalformedURLException malformedURLException) {
            System.out.println("URL for downloading install_ocssw.py is not correct!");
        } catch (FileNotFoundException fileNotFoundException) {
            System.out.println("ocssw installation script failed to download. \n" +
                    "Please check network connection or 'seadas.ocssw.root' variable in the 'seadas.config' file. \n" +
                    "possible cause of error: " + fileNotFoundException.getMessage());
        } catch (IOException ioe) {
            System.out.println("ocssw installation script failed to download. \n" +
                    "Please check network connection or 'seadas.ocssw.root' variable in the \"seadas.config\" file. \n" +
                    "possible cause of error: " + ioe.getLocalizedMessage());
        } finally {

            return ocsswInstalScriptDownloadSuccessful;
        }
    }

    private static boolean ocsswInstalScriptDownloadSuccessful = false;

    private void writeToFile(String content) {
        FileOutputStream fop = null;
        File file;
        try {

            file = new File("/home/aynur/cmdArray.txt");
            fop = new FileOutputStream(file);

            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            // get the content in bytes
            byte[] contentInBytes = content.getBytes();

            fop.write(contentInBytes);
            fop.flush();
            fop.close();

            System.out.println("Done");

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fop != null) {
                    fop.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
