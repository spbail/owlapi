package org.coode.owl.functionalrenderer;

import org.coode.string.EscapeUtils;
import org.coode.xml.OWLOntologyNamespaceManager;
import org.semanticweb.owl.model.*;
import org.semanticweb.owl.util.VersionInfo;
import org.semanticweb.owl.vocab.OWLXMLVocabulary;
import static org.semanticweb.owl.vocab.OWLXMLVocabulary.*;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.*;
/*
 * Copyright (C) 2006, University of Manchester
 *
 * Modifications to the initial code base are copyright of their
 * respective authors, or their employers as appropriate.  Authorship
 * of the modifications may be determined from the ChangeLog placed at
 * the end of this file.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 13-Dec-2006<br><br>
 */
public class OWLObjectRenderer implements OWLObjectVisitor {

    private OWLOntologyNamespaceManager nsm;

    private OWLOntology ontology;

    private Writer writer;

    private int pos;

    int lastNewLinePos;

    private boolean writeEnitiesAsURIs;

    private OWLObject focusedObject;

    public OWLObjectRenderer(OWLOntologyManager man, OWLOntology ontology, Writer writer) {
        this.ontology = ontology;
        this.writer = writer;
        writeEnitiesAsURIs = true;
        nsm = new OWLOntologyNamespaceManager(man, ontology);
        focusedObject = man.getOWLDataFactory().getOWLThing();
    }


    public void setFocusedObject(OWLObject focusedObject) {
        this.focusedObject = focusedObject;
    }


    private void writeNamespace(String prefix, String namespace) {
        write("Namespace");
        writeOpenBracket();
        write(prefix);
        write("=");
        write("<");
        write(namespace);
        write(">");
        writeCloseBracket();
        write("\n");
    }


    private void writeNamespaces() {
        if (nsm.getDefaultNamespace() != null) {
            writeNamespace("", nsm.getDefaultNamespace());
        }
        for (String prefix : nsm.getPrefixes()) {
            writeNamespace(prefix, nsm.getNamespaceForPrefix(prefix));
        }
    }


    private void write(OWLXMLVocabulary v) {
        write(v.getShortName());
    }


    private void write(String s) {
        try {
            int newLineIndex = s.indexOf('\n');
            if (newLineIndex != -1) {
                lastNewLinePos = pos + newLineIndex;
            }
            pos += s.length();
            writer.write(s);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private int getIndent() {
        return pos - lastNewLinePos - 1;
    }


    private void writeIndent(int indent) {
        for (int i = 0; i < indent; i++) {
            writeSpace();
        }
    }


    private void write(URI uri) {
        String uriString = uri.toString();
        String qname = nsm.getQName(uriString);
        if (qname != null && !qname.equals(uriString)) {
            write(qname);
        } else {
            write("<");
            write(uriString);
            write(">");
        }
    }


    private void writeFullIRI(IRI iri) {
        write("<");
        write(iri.toString());
        write(">");
    }


    public void visit(OWLOntology ontology) {
        writeNamespaces();
        write("\n\n");
        write(ONTOLOGY);
        write("(");
        if (ontology.getIRI() != null) {
            writeFullIRI(ontology.getIRI());
            if(ontology.getVersionIRI() != null) {
                write("\n");
                writeFullIRI(ontology.getVersionIRI());
            }
        }
        for(OWLImportsDeclaration decl : ontology.getImportsDeclarations()) {
            write(IMPORTS);
            write("(");
            writeFullIRI(decl.getIRI());
            write(")\n");
        }

        for(OWLAnnotation ontologyAnnotation : ontology.getAnnotations()) {
            ontologyAnnotation.accept(this);
            write("\n");
        }
        write("\n");

        Set<OWLAxiom> writtenAxioms = new HashSet<OWLAxiom>();

        for (OWLEntity ent : new TreeSet<OWLEntity>(ontology.getReferencedEntities())) {
            writtenAxioms.addAll(writeAxioms(ent));
        }

        Set<OWLAxiom> remainingAxioms = new TreeSet<OWLAxiom>(ontology.getAxioms());
        remainingAxioms.removeAll(writtenAxioms);

        for(OWLAxiom ax : remainingAxioms) {
            ax.accept(this);
            write("\n");
        }

        write("\n// ");
        write(VersionInfo.getVersionInfo().getGeneratedByMessage());
    }


    /**
     * Writes out the axioms that define the specified entity
     * @param entity The entity
     * @return The set of axioms that was written out
     */
    public Set<OWLAxiom> writeAxioms(OWLEntity entity) {
        Set<OWLAxiom> writtenAxioms = new HashSet<OWLAxiom>();
        setFocusedObject(entity);
        writtenAxioms.addAll(writeDeclarations(entity));
        writtenAxioms.addAll(writeAnnotations(entity));
        Set<OWLAxiom> axs = new TreeSet<OWLAxiom>();
        axs.addAll(entity.accept(new OWLEntityVisitorEx<Set<? extends OWLAxiom>>() {
            public Set<? extends OWLAxiom> visit(OWLClass cls) {
                return ontology.getAxioms(cls);
            }


            public Set<? extends OWLAxiom> visit(OWLObjectProperty property) {
                return ontology.getAxioms(property);
            }


            public Set<? extends OWLAxiom> visit(OWLDataProperty property) {
                return ontology.getAxioms(property);
            }


            public Set<? extends OWLAxiom> visit(OWLNamedIndividual individual) {
                return ontology.getAxioms(individual);
            }


            public Set<? extends OWLAxiom> visit(OWLDatatype datatype) {
                return Collections.emptySet();
//                return ontology.getAxioms(datatype);
            }


            public Set<? extends OWLAxiom> visit(OWLAnnotationProperty property) {
                return ontology.getAxioms(property);
            }
        }));
        for (OWLAxiom ax : axs) {
            ax.accept(this);
            writtenAxioms.add(ax);
            write("\n");
        }
        return writtenAxioms;
    }


    /**
     * Writes out the declaration axioms for the specified entity
     * @param entity The entity
     * @return The axioms that were written out
     */
    public Set<OWLAxiom> writeDeclarations(OWLEntity entity) {
        Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        for (OWLAxiom ax : ontology.getDeclarationAxioms(entity)) {
            ax.accept(this);
            axioms.add(ax);
            write("\n");
        }
        return axioms;
    }


    /**
     * Writes of the annotation for the specified entity
     * @param entity The entity
     * @return The set of axioms that were written out
     */
    public Set<OWLAxiom> writeAnnotations(OWLEntity entity) {
        Set<OWLAxiom> annotationAssertions = new HashSet<OWLAxiom>();
        for (OWLAnnotationAxiom ax : entity.getAnnotationAssertionAxioms(ontology)) {
            ax.accept(this);
            annotationAssertions.add(ax);
            write("\n");
        }
        return annotationAssertions;
    }


    public void write(OWLXMLVocabulary v, OWLObject o) {
        write(v);
        write("(");
        o.accept(this);
        write(")");
    }


    private void write(Collection<? extends OWLObject> objects) {
        if (objects.size() > 2) {
            int indent = getIndent();
            for (Iterator<? extends OWLObject> it = objects.iterator(); it.hasNext();) {
                it.next().accept(this);
                if (it.hasNext()) {
                    write(" ");
//                    writeIndent(indent);
                }
            }
        } else if (objects.size() == 2) {
            Iterator<? extends OWLObject> it = objects.iterator();
            OWLObject objA = it.next();
            OWLObject objB = it.next();
            OWLObject lhs, rhs;
            if (objA.equals(focusedObject)) {
                lhs = objA;
                rhs = objB;
            } else {
                lhs = objB;
                rhs = objA;
            }
            lhs.accept(this);
            writeSpace();
            rhs.accept(this);
        }
    }


    public void writeOpenBracket() {
        write("(");
    }


    public void writeCloseBracket() {
        write(")");
    }


    public void writeSpace() {
        write(" ");
    }


    public void write(OWLAnnotation annotation) {

    }

    public void writeAnnotations(OWLAxiom ax) {
        for(OWLAnnotation anno : ax.getAnnotations()) {
            anno.accept(this);
            write(" ");
        }
    }


    public void writeAxiomStart(OWLXMLVocabulary v, OWLAxiom axiom) {
        write(v);
        writeOpenBracket();
        writeAnnotations(axiom);
    }


    public void writeAxiomEnd() {
        write(")");
    }


    public void writePropertyCharacteristic(OWLXMLVocabulary v, OWLAxiom ax, OWLPropertyExpression prop) {
        writeAxiomStart(v, ax);
        prop.accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLAsymmetricObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(ASYMMETRIC_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLClassAssertionAxiom axiom) {
        writeAxiomStart(CLASS_ASSERTION, axiom);
        axiom.getClassExpression().accept(this);
        writeSpace();
        axiom.getIndividual().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLDataPropertyAssertionAxiom axiom) {
        writeAxiomStart(DATA_PROPERTY_ASSERTION, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getSubject().accept(this);
        writeSpace();
        axiom.getObject().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLDataPropertyDomainAxiom axiom) {
        writeAxiomStart(DATA_PROPERTY_DOMAIN, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getDomain().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLDataPropertyRangeAxiom axiom) {
        writeAxiomStart(DATA_PROPERTY_RANGE, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getRange().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLSubDataPropertyOfAxiom axiom) {
        writeAxiomStart(SUB_DATA_PROPERTY_OF, axiom);
        axiom.getSubProperty().accept(this);
        writeSpace();
        axiom.getSuperProperty().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLDeclarationAxiom axiom) {
        writeAxiomStart(DECLARATION, axiom);
        writeEnitiesAsURIs = false;
        axiom.getEntity().accept(this);
        writeEnitiesAsURIs = true;
        writeAxiomEnd();
    }


    public void visit(OWLDifferentIndividualsAxiom axiom) {
        writeAxiomStart(DIFFERENT_INDIVIDUALS, axiom);
        write(axiom.getIndividuals());
        writeAxiomEnd();
    }


    public void visit(OWLDisjointClassesAxiom axiom) {
        writeAxiomStart(DISJOINT_CLASSES, axiom);
        write(axiom.getClassExpressions());
        writeAxiomEnd();
    }


    public void visit(OWLDisjointDataPropertiesAxiom axiom) {
        writeAxiomStart(DISJOINT_DATA_PROPERTIES, axiom);
        write(axiom.getProperties());
        writeAxiomEnd();
    }


    public void visit(OWLDisjointObjectPropertiesAxiom axiom) {
        writeAxiomStart(DISJOINT_OBJECT_PROPERTIES, axiom);
        write(axiom.getProperties());
        writeAxiomEnd();
    }


    public void visit(OWLDisjointUnionAxiom axiom) {
        writeAxiomStart(DISJOINT_UNION, axiom);
        axiom.getOWLClass().accept(this);
        writeSpace();
        write(axiom.getClassExpressions());
        writeAxiomEnd();
    }


    public void visit(OWLAnnotationAssertionAxiom axiom) {
        writeAxiomStart(ANNOTATION_ASSERTION, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getSubject().accept(this);
        writeSpace();
        axiom.getValue().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLEquivalentClassesAxiom axiom) {
        writeAxiomStart(EQUIVALENT_CLASSES, axiom);
        write(axiom.getClassExpressions());
        writeAxiomEnd();
    }


    public void visit(OWLEquivalentDataPropertiesAxiom axiom) {
        writeAxiomStart(EQUIVALENT_DATA_PROPERTIES, axiom);
        write(axiom.getProperties());
        writeAxiomEnd();
    }


    public void visit(OWLEquivalentObjectPropertiesAxiom axiom) {
        writeAxiomStart(EQUIVALENT_OBJECT_PROPERTIES, axiom);
        write(axiom.getProperties());
        writeAxiomEnd();
    }


    public void visit(OWLFunctionalDataPropertyAxiom axiom) {
        writePropertyCharacteristic(FUNCTIONAL_DATA_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLFunctionalObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(FUNCTIONAL_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


   


    public void visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(INVERSE_FUNCTIONAL_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLInverseObjectPropertiesAxiom axiom) {
        writeAxiomStart(INVERSE_OBJECT_PROPERTIES, axiom);
        axiom.getFirstProperty().accept(this);
        writeSpace();
        axiom.getSecondProperty().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(IRREFLEXIVE_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
        writeAxiomStart(NEGATIVE_DATA_PROPERTY_ASSERTION, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getSubject().accept(this);
        writeSpace();
        axiom.getObject().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
        writeAxiomStart(NEGATIVE_OBJECT_PROPERTY_ASSERTION, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getSubject().accept(this);
        writeSpace();
        axiom.getObject().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLObjectPropertyAssertionAxiom axiom) {
        writeAxiomStart(OBJECT_PROPERTY_ASSERTION, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getSubject().accept(this);
        writeSpace();
        axiom.getObject().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLSubPropertyChainOfAxiom axiom) {
        writeAxiomStart(SUB_OBJECT_PROPERTY_OF, axiom);
        write(SUB_OBJECT_PROPERTY_CHAIN);
        writeOpenBracket();
        for (Iterator<OWLObjectPropertyExpression> it = axiom.getPropertyChain().iterator(); it.hasNext();) {
            it.next().accept(this);
            if (it.hasNext()) {
                write(" ");
            }
        }
        writeCloseBracket();
        writeSpace();
        axiom.getSuperProperty().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLObjectPropertyDomainAxiom axiom) {
        writeAxiomStart(OBJECT_PROPERTY_DOMAIN, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getDomain().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLObjectPropertyRangeAxiom axiom) {
        writeAxiomStart(OBJECT_PROPERTY_RANGE, axiom);
        axiom.getProperty().accept(this);
        writeSpace();
        axiom.getRange().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLSubObjectPropertyOfAxiom axiom) {
        writeAxiomStart(SUB_OBJECT_PROPERTY_OF, axiom);
        axiom.getSubProperty().accept(this);
        writeSpace();
        axiom.getSuperProperty().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLReflexiveObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(REFLEXIVE_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLSameIndividualAxiom axiom) {
        writeAxiomStart(SAME_INDIVIDUALS, axiom);
        write(axiom.getIndividuals());
        writeAxiomEnd();
    }


    public void visit(OWLSubClassOfAxiom axiom) {
        writeAxiomStart(SUB_CLASS_OF, axiom);
        axiom.getSubClass().accept(this);
        writeSpace();
        axiom.getSuperClass().accept(this);
        writeAxiomEnd();
    }


    public void visit(OWLSymmetricObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(SYMMETRIC_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLTransitiveObjectPropertyAxiom axiom) {
        writePropertyCharacteristic(TRANSITIVE_OBJECT_PROPERTY, axiom, axiom.getProperty());
    }


    public void visit(OWLClass desc) {
        if (!writeEnitiesAsURIs) {
            write(CLASS);
            writeOpenBracket();
        }
        desc.getIRI().accept(this);
        if (!writeEnitiesAsURIs) {
            writeCloseBracket();
        }
    }


    private void writeRestriction(OWLXMLVocabulary v, OWLCardinalityRestriction restriction) {
        write(v);
        writeOpenBracket();
        write(Integer.toString(restriction.getCardinality()));
        writeSpace();
        restriction.getProperty().accept(this);
        if (restriction.isQualified()) {
            writeSpace();
            restriction.getFiller().accept(this);
        }
        writeCloseBracket();
    }


    private void writeRestriction(OWLXMLVocabulary v, OWLQuantifiedRestriction restriction) {
        writeRestriction(v, restriction.getProperty(), restriction.getFiller());
    }


    private void writeRestriction(OWLXMLVocabulary v, OWLPropertyExpression prop, OWLObject filler) {
        write(v);
        writeOpenBracket();
        prop.accept(this);
        writeSpace();
        filler.accept(this);
        writeCloseBracket();
    }


    public void visit(OWLDataAllValuesFrom desc) {
        writeRestriction(DATA_ALL_VALUES_FROM, desc);
    }


    public void visit(OWLDataExactCardinality desc) {
        writeRestriction(DATA_EXACT_CARDINALITY, desc);
    }


    public void visit(OWLDataMaxCardinality desc) {
        writeRestriction(DATA_MAX_CARDINALITY, desc);
    }


    public void visit(OWLDataMinCardinality desc) {
        writeRestriction(DATA_MIN_CARDINALITY, desc);
    }


    public void visit(OWLDataSomeValuesFrom desc) {
        writeRestriction(DATA_SOME_VALUES_FROM, desc);
    }


    public void visit(OWLDataHasValue desc) {
        writeRestriction(DATA_HAS_VALUE, desc.getProperty(), desc.getValue());
    }


    public void visit(OWLObjectAllValuesFrom desc) {
        writeRestriction(OBJECT_ALL_VALUES_FROM, desc);
    }


    public void visit(OWLObjectComplementOf desc) {
        write(OBJECT_COMPLEMENT_OF, desc.getOperand());
    }


    public void visit(OWLObjectExactCardinality desc) {
        writeRestriction(OBJECT_EXACT_CARDINALITY, desc);
    }


    public void visit(OWLObjectIntersectionOf desc) {
        write(OBJECT_INTERSECTION_OF);
        writeOpenBracket();
        write(desc.getOperands());
        writeCloseBracket();
    }


    public void visit(OWLObjectMaxCardinality desc) {
        writeRestriction(OBJECT_MAX_CARDINALITY, desc);
    }


    public void visit(OWLObjectMinCardinality desc) {
        writeRestriction(OBJECT_MIN_CARDINALITY, desc);
    }


    public void visit(OWLObjectOneOf desc) {
        write(OBJECT_ONE_OF);
        writeOpenBracket();
        write(desc.getIndividuals());
        writeCloseBracket();
    }


    public void visit(OWLObjectHasSelf desc) {
        write(OBJECT_EXISTS_SELF, desc.getProperty());
    }


    public void visit(OWLObjectSomeValuesFrom desc) {
        writeRestriction(OBJECT_SOME_VALUES_FROM, desc);
    }


    public void visit(OWLObjectUnionOf desc) {
        write(OBJECT_UNION_OF);
        writeOpenBracket();
        write(desc.getOperands());
        writeCloseBracket();
    }


    public void visit(OWLObjectHasValue desc) {
        writeRestriction(OBJECT_HAS_VALUE, desc.getProperty(), desc.getValue());
    }


    public void visit(OWLDataComplementOf node) {
        write(DATA_COMPLEMENT_OF, node.getDataRange());
    }


    public void visit(OWLDataOneOf node) {
        write(DATA_ONE_OF);
        write("(");
        write(node.getValues());
        write(")");
    }


    public void visit(OWLDatatype node) {
        if(!writeEnitiesAsURIs) {
            write(DATATYPE);
            writeOpenBracket();
        }
        node.getIRI().accept(this);
        if(!writeEnitiesAsURIs) {
            writeCloseBracket();
        }
    }


    public void visit(OWLDatatypeRestriction node) {
        write(DATATYPE_RESTRICTION);
        writeOpenBracket();
        node.getDatatype().accept(this);
        for (OWLFacetRestriction restriction : node.getFacetRestrictions()) {
            writeSpace();
            restriction.accept(this);
        }
        writeCloseBracket();
    }


    public void visit(OWLFacetRestriction node) {
        write(node.getFacet().getShortName());
        writeSpace();
        node.getFacetValue().accept(this);
    }


    public void visit(OWLTypedLiteral node) {
        write("\"");
        write(EscapeUtils.escapeString(node.getLiteral()));
        write("\"");
        write("^^");
        write(node.getDatatype().getURI());
    }


    public void visit(OWLRDFTextLiteral node) {
        write("\"");
        write(EscapeUtils.escapeString(node.getLiteral()));
        write("\"");
        write("@");
        write(node.getLang());
    }


    public void visit(OWLDataProperty property) {
        if (!writeEnitiesAsURIs) {
            write(DATA_PROPERTY);
            writeOpenBracket();
        }
        property.getIRI().accept(this);
        if (!writeEnitiesAsURIs) {
            writeCloseBracket();
        }
    }


    public void visit(OWLObjectProperty property) {
        if (!writeEnitiesAsURIs) {
            write(OBJECT_PROPERTY);
            writeOpenBracket();
        }
        property.getIRI().accept(this);
        if (!writeEnitiesAsURIs) {
            writeCloseBracket();
        }
    }


    public void visit(OWLObjectPropertyInverse property) {
        write(INVERSE_OBJECT_PROPERTY);
        writeOpenBracket();
        property.getInverse().accept(this);
        writeCloseBracket();
    }


    public void visit(OWLNamedIndividual individual) {
        if (!writeEnitiesAsURIs) {
            write(INDIVIDUAL);
            writeOpenBracket();
        }
        individual.getIRI().accept(this);
        if (!writeEnitiesAsURIs) {
            writeCloseBracket();
        }
    }

    public void visit(OWLHasKeyAxiom axiom) {
        writeAxiomStart(HAS_KEY, axiom);
        axiom.getClassExpression().accept(this);
        write(" ");
        for(Iterator<OWLPropertyExpression>  it = axiom.getPropertyExpressions().iterator(); it.hasNext(); ) {
            OWLPropertyExpression prop = it.next();
            prop.accept(this);
            if(it.hasNext()) {
                write(" ");
            }
        }
        writeAxiomEnd();
    }

    public void visit(OWLAnnotationPropertyDomainAxiom axiom) {
        writeAxiomStart(ANNOTATION_PROPERTY_DOMAIN, axiom);
        axiom.getProperty().accept(this);
        write(" ");
        axiom.getDomain().accept(this);
        writeAxiomEnd();
    }

    public void visit(OWLAnnotationPropertyRangeAxiom axiom) {
        writeAxiomStart(ANNOTATION_PROPERTY_RANGE, axiom);
        axiom.getProperty().accept(this);
        write(" ");
        axiom.getRange().accept(this);
        writeAxiomEnd();
    }

    public void visit(OWLSubAnnotationPropertyOfAxiom axiom) {
        writeAxiomStart(SUB_ANNOTATION_PROPERTY_OF, axiom);
        axiom.getSubProperty().accept(this);
        write(" ");
        axiom.getSuperProperty().accept(this);
        writeAxiomEnd();
    }

    public void visit(OWLDataIntersectionOf node) {
        write(DATA_INTERSECTION_OF);
        writeOpenBracket();
        write(node.getOperands());
        writeCloseBracket();
    }

    public void visit(OWLDataUnionOf node) {
        write(DATA_UNION_OF);
        writeOpenBracket();
        write(node.getOperands());
        writeCloseBracket();
    }

    public void visit(OWLAnnotationProperty property) {
        if(!writeEnitiesAsURIs) {
            write(ANNOTATION_PROPERTY);
            writeOpenBracket();
        }
        property.getIRI().accept(this);
        if(!writeEnitiesAsURIs) {
            writeCloseBracket();
        }
    }

    public void visit(OWLAnonymousIndividual individual) {
        write("_:");
        write(individual.getID().toString());
    }

    public void visit(IRI iri) {
        write(iri.toURI());
    }

    public void visit(OWLAnnotation node) {
        write(ANNOTATION);
        write("(");
        for(OWLAnnotation anno : node.getAnnotations()) {
            anno.accept(this);
            write(" ");
        }
        node.getProperty().accept(this);
        write(" ");
        node.getValue().accept(this);
        write(")");
    }


    public void visit(OWLDatatypeDefinition axiom) {
        writeAxiomStart(DATATYPE_DEFINITION, axiom);
        axiom.getDatatype().accept(this);
        writeSpace();
        axiom.getDataRange().accept(this);
        writeAxiomEnd();
    }


    public void visit(SWRLRule rule) {
        // TODO:
    }


    public void visit(SWRLAtomIndividualObject node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLClassAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLDataRangeAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLObjectPropertyAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLDataValuedPropertyAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLBuiltInAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLAtomDVariable node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLAtomIVariable node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLAtomConstantObject node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLDifferentFromAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }


    public void visit(SWRLSameAsAtom node) {
        throw new RuntimeException("NOT IMPLEMENTED!");
    }
}
