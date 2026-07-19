package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * Wrapper NeTEx per i frame eterogenei dentro CompositeFrame.
 * Ordine: ResourceFrame (risorse) → SiteFrame (fermate) → ServiceFrame (rete) → TimetableFrame (corse).
 */
@Data
public class FramesDTO {

    @JacksonXmlProperty(localName = "ResourceFrame")
    private ResourceFrameDTO resourceFrame;

    @JacksonXmlProperty(localName = "SiteFrame")
    private SiteFrameDTO siteFrame;

    @JacksonXmlProperty(localName = "ServiceFrame")
    private ServiceFrameDTO serviceFrame;

    @JacksonXmlProperty(localName = "TimetableFrame")
    private TimetableFrameDTO timetableFrame;
}
