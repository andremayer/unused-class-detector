package com.mayer.clazz.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class UnusedClassDetector {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java -jar unused-class-detector.jar <folder_path_to_scan>");
            return;
        }

        Path folderPath = Paths.get(args[0]);
        if (!Files.isDirectory(folderPath)) {
            System.out.println("Invalid folder path: " + folderPath);
            return;
        }

        Map<String, Set<String>> classReferencesMap = new HashMap<>();

        Files.walk(folderPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> populateDeclaredClasses(path, classReferencesMap));

        Files.walk(folderPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> populateReferencedClasses(path, classReferencesMap));

        System.out.println("Unused classes in your codebase:");
        classReferencesMap.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty()) 
                .map(Map.Entry::getKey)
                .forEach(System.out::println);
    }

    private static void populateDeclaredClasses(Path filePath, Map<String, Set<String>> classReferencesMap) {
        try {
            CompilationUnit compUnit = StaticJavaParser.parse(filePath);
            String packageName = compUnit.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

            compUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String fullyQualifiedName = packageName.isEmpty() ? classDecl.getNameAsString()
                        : packageName + "." + classDecl.getNameAsString();
                classReferencesMap.putIfAbsent(fullyQualifiedName, new HashSet<>());
            });

        } catch (IOException e) {
            System.err.println("Failed to analyze file: " + filePath + " (" + e.getMessage() + ")");
        }
    }

    private static void populateReferencedClasses(Path filePath, Map<String, Set<String>> classReferencesMap) {
        try {
            CompilationUnit compUnit = StaticJavaParser.parse(filePath);

            Map<String, String> importMap = new HashMap<>();
            String packageName = compUnit.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

            compUnit.findAll(ImportDeclaration.class).forEach(importDecl -> {
                String fullyQualifiedName = importDecl.getNameAsString();
                String simpleName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
                importMap.put(simpleName, fullyQualifiedName);

                classReferencesMap.computeIfPresent(fullyQualifiedName, (key, references) -> {
                    references.add(filePath.toString());
                    return references;
                });
            });

            compUnit.findAll(NameExpr.class).forEach(nameExpr -> {
                String simpleName = nameExpr.getNameAsString();

                String fullyQualifiedName = importMap.getOrDefault(simpleName, packageName + "." + simpleName);

                classReferencesMap.computeIfPresent(fullyQualifiedName, (key, references) -> {
                    references.add(filePath.toString());
                    return references;
                });
            });

        } catch (IOException e) {
            System.err.println("Failed to analyze file: " + filePath + " (" + e.getMessage() + ")");
        }
    }


}
