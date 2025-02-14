package com.axlabs.neo.grantshares;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.NefFile;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.serialization.exceptions.DeserializationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.neow3j.contract.ContractUtils.writeContractManifestFile;
import static io.neow3j.contract.ContractUtils.writeNefFile;

public class CompilationHelper {

    // region compilation

    static void compileAndWriteNefAndManifestFiles(Class<?> contractClass) throws IOException {
        CompilationUnit compUnit = new Compiler().compile(contractClass.getCanonicalName());
        writeNefAndManifestFiles(compUnit);
    }

    public static void writeNefAndManifestFiles(CompilationUnit compUnit) throws IOException {
        // Write contract (compiled, NEF) to the disk
        Path buildNeow3jPath = Paths.get("build", "neow3j");
        buildNeow3jPath.toFile().mkdirs();
        writeNefFile(compUnit.getNefFile(), compUnit.getManifest().getName(), buildNeow3jPath);

        // Write manifest to the disk
        writeContractManifestFile(compUnit.getManifest(), buildNeow3jPath);
    }

    // endregion compilation
    // region read compilation output

    static NefFile readNefFile(String contractName) throws IOException, DeserializationException {
        File contractNefFile = Paths.get("build", "neow3j", contractName + ".nef").toFile();
        return NefFile.readFromFile(contractNefFile);
    }

    static ContractManifest readManifest(String contractName) throws IOException {
        File contractManifestFile = Paths.get("build", "neow3j", contractName + ".manifest.json").toFile();
        ContractManifest manifest;
        try (FileInputStream s = new FileInputStream(contractManifestFile)) {
            manifest = ObjectMapperFactory.getObjectMapper().readValue(s, ContractManifest.class);
        }
        return manifest;
    }

    // endregion read compilation output

}
