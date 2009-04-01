package org.coode.owl.rdfxml.parser;

import org.semanticweb.owl.model.OWLException;
import org.semanticweb.owl.vocab.OWLRDFVocabulary;

import java.net.URI;
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
 * Date: 08-Dec-2006<br><br>
 */
public class TypeFunctionalPropertyHandler extends BuiltInTypeHandler {

    public TypeFunctionalPropertyHandler(OWLRDFConsumer consumer) {
        super(consumer, OWLRDFVocabulary.OWL_FUNCTIONAL_PROPERTY.getURI());
    }


    public boolean canHandleStreaming(URI subject, URI predicate, URI object) throws OWLException {
        return getConsumer().isObjectPropertyOnly(subject) ||
                getConsumer().isDataPropertyOnly(subject);
    }


    public void handleTriple(URI subject, URI predicate, URI object) throws OWLException {
        if (getConsumer().isObjectPropertyOnly(subject)) {
            getConsumer().addOWLObjectProperty(subject);
            addAxiom(getDataFactory().getOWLFunctionalObjectPropertyAxiom(translateObjectProperty(subject)));
            consumeTriple(subject, predicate, object);
        } else if (getConsumer().isDataPropertyOnly(subject)) {
            getConsumer().addOWLDataProperty(subject);
            addAxiom(getDataFactory().getOWLFunctionalDataPropertyAxiom(translateDataProperty(subject)));
            consumeTriple(subject, predicate, object);
        } else {
            // Assume object property! :(
            // I suppose that we could check where the predicate is used, but I'm losing the will to live!
            addAxiom(getDataFactory().getOWLFunctionalObjectPropertyAxiom(translateObjectProperty(subject)));
            consumeTriple(subject, predicate, object);
        }

    }
}