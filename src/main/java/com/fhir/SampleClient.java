package com.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import com.google.common.base.Stopwatch;
import org.hl7.fhir.r4.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SampleClient {
    private static final String DATE_FORMAT = "MMM d, yyyy - hh:mm:ss";
    private DateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
    private static final String FILE_NAME = "lastnames.txt";

    // use compareToIgnoreCase() to ignore lower case and upper case differences in the name while sorting
    // eg- 'jack' comes before 'John'
    //      jack, smith
    //      John, Smith
    Comparator<Bundle.BundleEntryComponent> byFirstName =
            (Bundle.BundleEntryComponent o1, Bundle.BundleEntryComponent o2) ->
            {
                List o1givenNames = ((Patient) o1.getResource()).getName().get(0).getGiven();
                List o2givenNames = ((Patient) o2.getResource()).getName().get(0).getGiven();
                if (o1givenNames.isEmpty() || o2givenNames.isEmpty()) {
                    return -1;
                }
                return   o1givenNames.get(0).toString().compareToIgnoreCase(o2givenNames.get(0).toString());
            };

    public static void main(String[] theArgs) {
        Map<Integer, Long> timerMap = new SampleClient().executeSearch();

        // print timer
        timerMap.forEach((K,V)->{
                System.out.println("loop-" + K + " " + V + " ms");
        });

    }

    public Map<Integer, Long> executeSearch() {
        CustomClientInterceptor clientInterceptor = new CustomClientInterceptor();
        Map timerMap = new LinkedHashMap<Integer, Long>();

        // Create a FHIR client
        FhirContext fhirContext = FhirContext.forR4();
        IGenericClient client = fhirContext.newRestfulGenericClient("http://hapi.fhir.org/baseR4");
        client.registerInterceptor(new LoggingInterceptor(false));
        client.registerInterceptor(clientInterceptor);

        for (int repetitionCount = 1; repetitionCount <= 3; repetitionCount++) {
            final AtomicInteger count = new AtomicInteger();
            boolean cachingDisabled = repetitionCount < 3 ? false : true;

            // read file into stream
            try (Stream<String> stream = Files.lines(Paths.get(FILE_NAME))) {
                stream.forEach(lastName -> {
                    findAndListPatients(client, lastName, cachingDisabled);
                    count.getAndAdd(1);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

            timerMap.put(repetitionCount, clientInterceptor.elapsedTime / count.get());

            // add delay
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            clientInterceptor.resetElapsedTime();
        }

        return timerMap;
    }

    private void findAndListPatients(IGenericClient client, String lastName, boolean cachingDisabled) {
        // Search for Patient resources.
        // Use matchesExactly() to filter only patients matching exact last name, eg- for query for 'Rogers' not to get "Rogerson"
        // Parameterize cacheControl() to toggle caching
        Bundle response = client
                .search()
                .forResource("Patient")
                .where(Patient.FAMILY.matchesExactly().value(lastName))
                .returnBundle(Bundle.class)
                .cacheControl(new CacheControlDirective().setNoCache(cachingDisabled))
                .execute();

        response.getEntry().sort(byFirstName);

        response.getEntry().stream().forEach(entry -> {
            Patient patient = (Patient) entry.getResource();
            List<HumanName> names = patient.getName();
            if (!names.isEmpty()) {
                List<StringType> givenNames = names.get(0).getGiven();
                if (!givenNames.isEmpty()) {
                    System.out.println(givenNames.get(0) + ", " + names.get(0).getFamily() + "\t dob: " + (patient.getBirthDate() != null ? formatter.format(patient.getBirthDate()) : "unknown"));
                }
            }
        });
    }

    public static class CustomClientInterceptor implements IClientInterceptor {
        private Stopwatch watch;
        private long elapsedTime;

        @Override
        public void interceptRequest(IHttpRequest iHttpRequest) {
            this.watch = Stopwatch.createStarted();
        }

        @Override
        public void interceptResponse(IHttpResponse iHttpResponse) throws IOException {
            this.elapsedTime += this.watch.elapsed(TimeUnit.MILLISECONDS);
        }

        void resetElapsedTime() {
            this.elapsedTime = 0;
        }
    }

}

