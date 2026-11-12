import { ReferenceHeader } from "../../../components/reference-header";
import { TrainDetailsExperience } from "../../../components/train-details-experience";

export const metadata = {
  title: "Train route · Mobile",
};

export default function MobileTrainDetailsPage() {
  return (
    <div className="page-shell bg-[#040914] text-[#eaf3ff]">
      <ReferenceHeader experience="mobile" activeTab="train-info" />
      <TrainDetailsExperience device="mobile" />
      <footer className="site-footer">RailConnect is an independent passenger platform.</footer>
    </div>
  );
}
