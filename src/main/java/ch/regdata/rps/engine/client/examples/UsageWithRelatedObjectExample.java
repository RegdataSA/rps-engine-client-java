package ch.regdata.rps.engine.client.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import ch.regdata.rps.engine.client.RPSEngine;
import ch.regdata.rps.engine.client.RPSEngineConverter;
import ch.regdata.rps.engine.client.enginecontext.RPSEngineContextJsonFileProvider;
import ch.regdata.rps.engine.client.enginecontext.RPSEngineContextResolver;
import ch.regdata.rps.engine.client.http.HttpClientEngineProvider;
import ch.regdata.rps.engine.client.mapping.RPSMapping;
import ch.regdata.rps.engine.client.model.api.value.IRPSValue;
import ch.regdata.rps.engine.client.model.api.value.RPSValue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;

public class UsageWithRelatedObjectExample {


    public static void main(String[] args) {

        // Get the engine provider with connection details
        HttpClientEngineProvider engineProvider = EngineProviderFactory.getEngineProvider();

        try {
            RPSEngineContextJsonFileProvider engineContextJsonFileProvider = new RPSEngineContextJsonFileProvider(
                    (Paths.get(UsageWithDependenciesExample.class.getClassLoader().getResource("RightsContexts.json").toURI())).toString(),
                    (Paths.get(UsageWithDependenciesExample.class.getClassLoader().getResource("ProcessingContexts.json").toURI())).toString()
            );

            engineContextJsonFileProvider.initialize();

            System.out.println("--- Example of protection JSON file with related object ---");
            String originalJson = new String(Files.readAllBytes(
                    Paths.get(UsageWithDependenciesExample.class.getClassLoader().getResource("ExampleOfJsonToProtect.json").toURI())));
            ObjectMapper mapper = new ObjectMapper();
            // A JSON object. Key value pairs are unordered. JSONObject supports java.util.Map interface.
            JsonNode rootJObject = mapper.readTree(originalJson);
            System.out.println("Original JSON:");
            System.out.println(rootJObject.toPrettyString());
            System.out.println();
            ArrayList<IRPSValue> rpsValues = new ArrayList();
            Iterator<String> users = rootJObject.fieldNames();
            while (users.hasNext()) {
                String userName = users.next();
                JsonNode user = rootJObject.get(userName);
                Iterator<String> propertyNames = user.fieldNames();
                while (propertyNames.hasNext()) {
                    String propertyName = propertyNames.next();
                    JsonNode property = user.get(propertyName);
                    RPSValue rpsValue = new RPSValue(new RPSMapping("User", propertyName), property.textValue());
                    rpsValue.setTarget(user);
                    rpsValues.add(rpsValue);
                }
            }

            RPSEngine engine = new RPSEngine(engineProvider,
                    new RPSEngineConverter(),
                    new RPSEngineContextResolver(engineContextJsonFileProvider));

            // This method will do the call to RPS Engine API.
            engine.transform(rpsValues.toArray(new IRPSValue[0]), "Admin", "Protect");

            // Get the protected values
            for (IRPSValue irpsValue : rpsValues) {
                RPSValue rpsValue = (RPSValue) irpsValue;
                ((ObjectNode) rpsValue.getTarget()).set(rpsValue.getMapping().getPropertyName(), new TextNode(rpsValue.getTransformed()));
            }

            System.out.println("Transformed JSON:");
            System.out.println(rootJObject.toPrettyString());
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
