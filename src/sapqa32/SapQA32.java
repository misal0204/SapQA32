package sapqa32;

import com.sap.conn.jco.AbapException;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;
import com.sap.conn.jco.ext.DestinationDataProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Misael Recinos
 */
public class SapQA32 {

    final static String ABAP_AS_POOLED = "ABAP_AS_WITH_POOL";
    private final static String jco_ashost = "10.10.100.52";
    private final static String jco_asrouter = "/H/168.234.192.205/S/3299";
    private final static String jco_sysnr = "00";
    private final static String jco_client = "241";
    private final static String jco_user = "jrecinos";//molinosqm02 - //jrecinos - // nguzman
    private final static String jco_passwd = "Manager$15";//barreritas$00 - // Manager$15 - //minimi10
    private final static String jco_lang = "ES";
    private final static String jco_pool_capacity = "3";
    private final static String jco_peak_limit = "10";

    static {
        Properties connectProperties = new Properties();
        connectProperties.setProperty(DestinationDataProvider.JCO_ASHOST, jco_ashost);
        connectProperties.setProperty(DestinationDataProvider.JCO_SAPROUTER, jco_asrouter);
        connectProperties.setProperty(DestinationDataProvider.JCO_SYSNR, jco_sysnr);
        connectProperties.setProperty(DestinationDataProvider.JCO_CLIENT, jco_client);
        connectProperties.setProperty(DestinationDataProvider.JCO_USER, jco_user);
        connectProperties.setProperty(DestinationDataProvider.JCO_PASSWD, jco_passwd);
        connectProperties.setProperty(DestinationDataProvider.JCO_LANG, jco_lang);
        //Numero máximo de connection que puede ser abierto el destino
        connectProperties.setProperty(DestinationDataProvider.JCO_POOL_CAPACITY, jco_pool_capacity);

        //Número máximo de conexiones activas
        connectProperties.setProperty(DestinationDataProvider.JCO_PEAK_LIMIT, jco_peak_limit);

        createDataFile(ABAP_AS_POOLED, "jcoDestination", connectProperties);
    }

    static void createDataFile(String name, String suffix, Properties properties) {
        File cfg = new File(name + "." + suffix);
        if (!cfg.exists()) {
            try {
                FileOutputStream fos = new FileOutputStream(cfg, false);
                properties.store(fos, "Creación de archivo exitoso");
                fos.close();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create the destination " + cfg.getName(), e);
            }
        }
    }

    public static void exeFunctionCall() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        JCoFunction function = destination.getRepository().getFunction("STFC_CONNECTION");

        if (function == null) {
            throw new RuntimeException("STFC_CONNECTION not found in SAP.");
        }
        //Se recupera el importparmeterList() y se fija un valor
        function.getImportParameterList().setValue("REQUTEXT", "Conexión realizada con exíto");

        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
            return;
        }
        System.out.println("STFC_CONNECTION finished: ");
        System.out.println("Echo: " + function.getExportParameterList().getString("ECHOTEXT"));
        System.out.println("Response: " + function.getExportParameterList().getString("RESPTEXT"));
        System.out.println();

    }

    public static void step4WorkWithTable() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        JCoFunction function = destination.getRepository().getFunction("BAPI_INSPLOT_GETLIST");
        if (function == null) {
            throw new RuntimeException("BAPI_INSPLOT_GETLIST not found in SAP.");
        }

        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
            return;
        }

        JCoStructure returnStructure = function.getExportParameterList().getStructure("RETURN");
        if (!(returnStructure.getString("TYPE").equals("") || returnStructure.getString("TYPE").equals("S"))) {
            throw new RuntimeException(returnStructure.getString("MESSAGE"));
        }

        JCoTable codes = function.getTableParameterList().getTable("INSPLOT_LIST");
        for (int i = 0; i < codes.getNumRows(); i++) {
            codes.setRow(i);
            if (!codes.getString("ORDER_NO").equals("")
                    || codes.getString("MATERIAL").equals("")
                    || codes.getString("BATCH").equals("")) {

                System.out.println("Lote de inspección: " + codes.getString("INSPLOT")
                        + '\t' + "Planta: " + codes.getString("PLANT")
                        + '\t' + "Número de orden: " + codes.getString("ORDER_NO")
                        + '\t' + "Material: " + codes.getString("MATERIAL")
                        + '\t' + "Lote: " + codes.getString("BATCH")
                        + '\t' + "Fecha de creación: " + codes.getString("CREAT_DAT"));
            }
        }

        //move the table cursor to first row
        codes.firstRow();
        System.out.println("GetDetail ------------------------------------------------------------------");
        for (int i = 0; i < codes.getNumRows(); i++, codes.nextRow()) {
            function = destination.getRepository().getFunction("BAPI_INSPLOT_GETDETAIL");
            if (function == null) {
                throw new RuntimeException("BAPI_INSPLOT_GETDETAIL not found in SAP.");
            }

            function.getImportParameterList().setValue("NUMBER", codes.getString("INSPLOT"));

            //We do not need the addresses, so set the corresponding parameter to inactive.
            //Inactive parameters will be  either not generated or at least converted.  
            //function.getExportParameterList().setActive("GENERAL_DATA", false);
            try {
                function.execute(destination);
            } catch (AbapException e) {
                System.out.println(e.toString());
                return;
            }

            returnStructure = function.getExportParameterList().getStructure("RETURN");
            if (!(returnStructure.getString("TYPE").equals("")
                    || returnStructure.getString("TYPE").equals("S")
                    || returnStructure.getString("TYPE").equals("W"))) {
                throw new RuntimeException(returnStructure.getString("MESSAGE"));
            }

            JCoStructure detail = function.getExportParameterList().getStructure("GENERAL_DATA");

            if (!(detail.getString("ORDERID").equals(""))) {

                System.out.println("Lote de inspección: " + detail.getString("INSPLOT") + '\t'
                        + "Clase de inspección: " + detail.getString("INSP_TYPE") + '\t'
                        + "Lote: " + detail.getString("BATCH") + '\t'
                        + "Orden: " + detail.getString("ORDERID"));
            }
        }//for
    }

    public static void Bapi_qe02() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        //JCoFunction function = destination.getRepository().getFunction("BAPI_INSPOPER_GETCHAR");
        JCoFunction function = destination.getRepository().getFunction("BAPI_INSPCHAR_GETRESULT");
        //JCoFunction function = destination.getRepository().getFunction("BAPI_INSPLOT_GETOPERATIONS");
        //JCoFunction function = destination.getRepository().getFunction("BAPI_INSPOPER_GETCHAR");

        function.getImportParameterList().setValue("INSPLOT", "10000000522");
        function.getImportParameterList().setValue("INSPOPER", "0010");
        function.getImportParameterList().setValue("INSPCHAR", "0010");
        if (function == null) {
            throw new RuntimeException("BAPI_INSPOPER_RECORDRESULTS not found in SAP.");
        }
        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
            return;
        }
        /*JCoStructure returnStructure = function.getExportParameterList().getStructure("RETURN");
         if (!(returnStructure.getString("TYPE").equals("") || returnStructure.getString("TYPE").equals("S"))) {
         throw new RuntimeException(returnStructure.getString("MESSAGE"));
         }*/

        JCoStructure resultados = function.getExportParameterList().getStructure("CHAR_RESULT");

        String a = resultados.getString("INSPLOT");
        String b = resultados.getString("EVALUATED");
        String c = resultados.getString("EVALUATION");
        String d = resultados.getString("REMARK");
        System.out.println("lote de inspeccion: " + a);
        System.out.println("evaluted: " + b);
        System.out.println("evaluación: " + c);
        System.out.println("descripcion: " + d);

        /*       
         JCoTable codes = function.getTableParameterList().getTable("SINGLE_RESULTS");
         for (int i = 0; i < codes.getNumRows(); i++) {
         codes.setRow(i);

         System.out.println("lote de inspección: " + codes.getString("INSPLOT"));
         System.out.println("operación: " + codes.getString("RES_VALUE"));
         }
         */
        /*if ((codes != null) && ((numrows = codes.getNumRows()) > 0)) {
         System.out.println("Con valores");

         for (int i = 0; i < codes.getNumRows(); i++) {
         codes.setRow(i);

         System.out.println("Caracteristica: " + codes.getString("MERKNR"));
         System.out.println("Centro: " + codes.getString("QMTB_WERKS"));
         }
         } else {
         System.out.println("Sin valores");
         }*/
        //codes.firstRow();
    }

    public static void Bapi_get() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        JCoFunction function = destination.getRepository().getFunction("BAPI_INSPCHAR_GETRESULT");

        function.getImportParameterList().setValue("INSPLOT", "10000000522");
        function.getImportParameterList().setValue("INSPOPER", "0010");
        function.getImportParameterList().setValue("INSPCHAR", "0020");
        if (function == null) {
            throw new RuntimeException("BAPI_INSPOPER_RECORDRESULTS not found in SAP.");
        }
        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
            return;
        }
        /*JCoStructure returnStructure = function.getExportParameterList().getStructure("RETURN");
         if (!(returnStructure.getString("TYPE").equals("") || returnStructure.getString("TYPE").equals("S"))) {
         throw new RuntimeException(returnStructure.getString("MESSAGE"));
         }*/

        JCoStructure resultados = function.getExportParameterList().getStructure("CHAR_RESULT");

        System.out.println(resultados.getString("INSPLOT"));
        System.out.println(resultados.getString("MEAN_VALUE"));     //si son decimales (Datos cuatitativos)
        System.out.println(resultados.getString("ORIGINAL_INPUT")); // si son decimales (Datos cuantitativos)
        System.out.println(resultados.getString("CODE1")); // resultado cuando solo es texto (solo codigo)
        System.out.println(resultados.getString("CODE_GRP1"));// Texto breve cuando es texto
        System.out.println("Cerrado: "+resultados.getString("CLOSED")); //X
        System.out.println("No valido: "+resultados.getString("CHAR_INVAL"));
        System.out.println("Resultado: "+resultados.getString("EVALUATION")); // A
        System.out.println("Valoración: "+resultados.getString("EVALUATED")); // X
        System.out.println(resultados.getString("START_DATE"));
        System.out.println(resultados.getString("START_TIME"));
        System.out.println(resultados.getString("INSPECTOR"));
        System.out.println(resultados.getString("REMARK"));

    }

    public static void Bapi_set() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        JCoFunction function = destination.getRepository().getFunction("BAPI_INSPCHAR_SETRESULT");

        function.getImportParameterList().setValue("INSPLOT", "10000000522");
        function.getImportParameterList().setValue("INSPOPER", "0010");
        function.getImportParameterList().setValue("INSPCHAR", "0010");
        if (function == null) {
            throw new RuntimeException("BAPI_INSPCHAR_SETRESULT not found in SAP.");
        }
        
        JCoParameterList importParam = function.getImportParameterList();
        JCoStructure structure = importParam.getStructure("CHAR_RESULT");

        //structure.setValue("REMARK", "Resultados de hiumedad 123");
        //structure.setValue("MEAN_VALUE", "0.45");
        structure.setValue("CLOSED", "");
        structure.setValue("EVALUATION", "");
        structure.setValue("EVALUATED", "");
        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
        }
    }

    public static void Bapi_getstatus() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        JCoFunction function = destination.getRepository().getFunction("BAPI_INSPCHAR_GETREQUIREMENTS");

        function.getImportParameterList().setValue("INSPLOT", "10000000522");
        function.getImportParameterList().setValue("INSPOPER", "0010");
        function.getImportParameterList().setValue("INSPCHAR", "0030");
        if (function == null) {
            throw new RuntimeException("BAPI_INSPCHAR_GETREQUIREMENTS not found in SAP.");
        }
        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
            return;
        }
        JCoStructure resultados = function.getExportParameterList().getStructure("REQUIREMENTS");

        String a = resultados.getString("INSPLOT");
        String b = resultados.getString("STATUS");
        String c = resultados.getString("CHAR_DESCR");
        String d = resultados.getString("CONFIRM_NO");
        System.out.println("lote de inspeccion: " + a);
        System.out.println("estatus: " + b);
        System.out.println("descripcion_char: " + c);
        System.out.println("# confirmación: " + d);
    }

    public static void Bapi_setstatus() throws JCoException {
        JCoDestination destination = JCoDestinationManager.getDestination(ABAP_AS_POOLED);
        JCoFunction function = destination.getRepository().getFunction("BAPI_INSPCHAR_GETREQUIREMENTS");

        function.getImportParameterList().setValue("INSPLOT", "10000000522");
        function.getImportParameterList().setValue("INSPOPER", "0010");
        function.getImportParameterList().setValue("INSPCHAR", "0030");
        if (function == null) {
            throw new RuntimeException("BAPI_INSPCHAR_GETREQUIREMENTS not found in SAP.");
        }

        JCoParameterList importParam = function.getImportParameterList();
        JCoStructure structure = importParam.getStructure("INSPCHAR");
        structure.setValue("STATUS", 2);
        
        try {
            function.execute(destination);
        } catch (AbapException e) {
            System.out.println(e.toString());
        }

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            exeFunctionCall();
            //step4WorkWithTable();
            //Bapi_qe02();
            //Bapi_get();
            Bapi_set();
            //Bapi_getstatus();
            //Bapi_setstatus();
        } catch (JCoException ex) {
            Logger.getLogger(SapQA32.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("Error en sap: " + ex.getMessage());
        }
    }

}
