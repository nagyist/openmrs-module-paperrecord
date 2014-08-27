package org.openmrs.module.paperrecord;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class RequestPaperRecordSynchronizationTest extends BaseModuleContextSensitiveTest {

    public static final int NUM_THREADS = 25;

    @Autowired
    private PaperRecordService paperRecordService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private LocationService locationService;

    @Before
    public void beforeAllTests() throws Exception {
        executeDataSet("paperRecordTestDataset.xml");
    }

    @Test
    public void shouldNotCreateMultiplePaperRecordRequests() {

        // note that I have confirmed that this does fail if run without the synchronized lock around the patient

        // I split the synchronization tests up into two different classes (CreatePaperRecordSynchronizationTest and RequestPaperRecordSynchronizationTest)
        // because for some reason when the tests were in the same class one always seemed to pass, even if the methods weren't within sync blocks

        // sanity check
        Patient patient = patientService.getPatient(7);
        assertThat(paperRecordService.getPaperRecordRequestsByPatient(patient).size(), is(1));  // there is one existing "SENT" request for this patient

        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < NUM_THREADS; ++i) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Context.openSession();
                    try {

                        authenticate();

                        Patient patient = patientService.getPatient(7);
                        Location medicalRecordLocation = locationService.getLocation(1);
                        Location requestLocation = locationService.getLocation(2);

                        paperRecordService.requestPaperRecord(patient, medicalRecordLocation, requestLocation);

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        Context.closeSession();
                    }
                }
            });
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // pass
            }
        }

        // only one paper record request should have been created (+ the existing sent request = 2 total requests)
        assertThat(paperRecordService.getPaperRecordRequestsByPatient(patient).size(), is(2));

    }

}
