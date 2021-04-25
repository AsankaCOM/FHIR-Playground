import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.List;

public class SampleClient {
    private static final String DATE_FORMAT = "MMM d, yyyy - hh:mm:ss";
    static DateFormat formatter = new SimpleDateFormat(DATE_FORMAT);

    public static void main(String[] theArgs) {

        // Create a FHIR client
        FhirContext fhirContext = FhirContext.forR4();
        IGenericClient client = fhirContext.newRestfulGenericClient("http://hapi.fhir.org/baseR4");
        client.registerInterceptor(new LoggingInterceptor(false));

        // Search for Patient resources
        Bundle response = client
                .search()
                .forResource("Patient")
                .where(Patient.FAMILY.matches().value("SMITH"))
                .returnBundle(Bundle.class)
                .execute();

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

}
